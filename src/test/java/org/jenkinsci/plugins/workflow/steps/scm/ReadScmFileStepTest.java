/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc.
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

import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public final class ReadScmFileStepTest {

    @RegisterExtension public static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @Test public void smokes(JenkinsRule r) throws Throwable {
        var sampleGitRepo = new GitSampleRepoRule();
        sampleGitRepo.before();
        try {
            sampleGitRepo.init();
            sampleGitRepo.write("config.txt", "trunk");
            sampleGitRepo.git("add", "config.txt");
            sampleGitRepo.git("commit", "--message=trunk");
            sampleGitRepo.git("checkout", "-b", "feat");
            sampleGitRepo.write("config.txt", "patched");
            sampleGitRepo.git("commit", "-a", "--message=feat");
            var p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("REPO")));
            p.setDefinition(new CpsFlowDefinition(
                """
                def txt = readScmFile path: 'config.txt', version: 'feat', scm: gitSource(REPO)
                echo "got $txt"
                """, true));
            r.assertLogContains("got patched", r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("REPO", sampleGitRepo.toString())))));
        } finally {
            sampleGitRepo.after();
        }
    }

}
