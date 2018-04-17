/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps.scm;

import hudson.model.Label;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.SubversionSCM;
import hudson.triggers.SCMTrigger;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.Iterator;
import java.util.List;

import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.subversion.SubversionSampleRepoRule;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class SCMStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleGitRepo = new GitSampleRepoRule();
    @Rule public SubversionSampleRepoRule sampleSvnRepo = new SubversionSampleRepoRule();

    @Issue("JENKINS-26100")
    @Test
    public void scmVars() throws Exception {
        rr.then(r -> {
            sampleSvnRepo.init();
            sampleSvnRepo.write("Jenkinsfile", "node('remote') {\n" +
                "    def svnRev = checkout(scm).SVN_REVISION\n" +
                "    echo \"SVN_REVISION is ${svnRev}\"\n" +
                "}\n");
            sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/Jenkinsfile");
            sampleSvnRepo.svnkit("commit", "--message=+Jenkinsfile", sampleSvnRepo.wc());
            long revision = sampleSvnRepo.revision();
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsScmFlowDefinition(new SubversionSCM(sampleSvnRepo.trunkUrl()), "Jenkinsfile"));
            r.createOnlineSlave(Label.get("remote"));
            WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            r.assertLogContains("SVN_REVISION is " + revision, b);
        });
    }

    @Issue("JENKINS-26761")
    @Test public void checkoutsRestored() throws Exception {
        rr.then(r -> {
            sampleGitRepo.init();
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.addTrigger(new SCMTrigger(""));
            r.createOnlineSlave(Label.get("remote"));
            p.setDefinition(new CpsFlowDefinition(
                "node('remote') {\n" +
                "    ws {\n" +
                "        git($/" + sampleGitRepo + "/$)\n" +
                "    }\n" +
                "}", true));
            p.save();
            WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
            r.assertLogContains("Cloning the remote Git repository", b);
            FileUtils.copyFile(new File(b.getRootDir(), "build.xml"), System.out);
        });
        rr.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            r.createOnlineSlave(Label.get("remote"));
            sampleGitRepo.write("nextfile", "");
            sampleGitRepo.git("add", "nextfile");
            sampleGitRepo.git("commit", "--message=next");
            sampleGitRepo.notifyCommit(r);
            WorkflowRun b = p.getLastBuild();
            assertEquals(2, b.number);
            r.assertLogContains("Cloning the remote Git repository", b); // new slave, new workspace
            List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
            assertEquals(1, changeSets.size());
            ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
            assertEquals(b, changeSet.getRun());
            assertEquals("git", changeSet.getKind());
            Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
            assertTrue(iterator.hasNext());
            ChangeLogSet.Entry entry = iterator.next();
            assertEquals("[nextfile]", entry.getAffectedPaths().toString());
            assertFalse(iterator.hasNext());
        });
    }

    @Issue("JENKINS-32214")
    @Test public void pollDuringBuild() throws Exception {
        rr.then(r -> {
            sampleSvnRepo.init();
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "semaphore 'before'\n" +
                "node {svn '" + sampleSvnRepo.trunkUrl() + "'}\n" +
                "semaphore 'after'", true));
            assertPolling(p, PollingResult.Change.INCOMPARABLE);
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.success("before/1", null);
            SemaphoreStep.waitForStart("after/1", b1);
            assertPolling(p, PollingResult.Change.NONE);
            SemaphoreStep.success("after/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b1));
            sampleSvnRepo.write("file2", "");
            sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/file2");
            sampleSvnRepo.svnkit("commit", "--message=+file2", sampleSvnRepo.wc());
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.success("before/2", null);
            SemaphoreStep.waitForStart("after/2", b2);
            assertPolling(p, PollingResult.Change.NONE);
            WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("before/3", b3);
            assertPolling(p, PollingResult.Change.NONE);
            sampleSvnRepo.write("file3", "");
            sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/file3");
            sampleSvnRepo.svnkit("commit", "--message=+file3", sampleSvnRepo.wc());
            assertPolling(p, PollingResult.Change.SIGNIFICANT);
        });
    }
    private static void assertPolling(WorkflowJob p, PollingResult.Change expectedChange) {
        assertEquals(expectedChange, p.poll(StreamTaskListener.fromStdout()).change);
    }

}
