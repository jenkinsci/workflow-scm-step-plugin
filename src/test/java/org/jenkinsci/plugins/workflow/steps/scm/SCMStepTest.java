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

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.NullSCM;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import jenkins.model.Jenkins;

import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.xml.sax.SAXException;

public class SCMStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Issue(value = { "JENKINS-57918", "JENKINS-59560" })
    @Test public void scmParsesUnmodifiedChangelogFile() {
        rr.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "node() {\n" +
                "  checkout([$class: 'InvalidChangelogSCM'])\n" +
                "}", true));
            r.buildAndAssertSuccess(p);
        });
    }

    @Test public void scmParsesChangelogFileFromFakeChangeLogSCM() {
        rr.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    "import org.jvnet.hudson.test.FakeChangeLogSCM\n" +
                    "def testSCM = new FakeChangeLogSCM()\n" +
                    "testSCM.addChange().withAuthor(/alice$BUILD_NUMBER/)\n" +
                    "node() {\n" +
                    "  checkout(testSCM)\n" +
                    "}", false));
            WorkflowRun b = r.buildAndAssertSuccess(p);
            assertThat(b.getCulpritIds(), Matchers.equalTo(Collections.singleton("alice1")));
        });
    }

    public static class InvalidChangelogSCM extends NullSCM {
        @DataBoundConstructor
        public InvalidChangelogSCM() { }
        @Override public void checkout(Run<?,?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
            // Unlike the superclass, which adds an XML header to the file, we just ignore the file.
        }
        @Override public ChangeLogParser createChangeLogParser() {
            return new ChangeLogParser() {
                public ChangeLogSet<? extends ChangeLogSet.Entry> parse(Run build, RepositoryBrowser<?> browser, File changelogFile) throws IOException, SAXException {
                    Jenkins.XSTREAM2.fromXML(changelogFile);
                    throw new AssertionError("The previous line should always fail because the file is empty");
                }
            };
        }
        @TestExtension("scmParsesUnmodifiedChangelogFile")
        public static class DescriptorImpl extends NullSCM.DescriptorImpl { }
    }

}
