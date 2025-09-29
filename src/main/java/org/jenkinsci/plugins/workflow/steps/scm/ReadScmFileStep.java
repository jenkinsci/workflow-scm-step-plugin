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

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Set;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

public final class ReadScmFileStep extends Step {

    private final SCMSource scm;
    private final String version;
    private final String path;

    @DataBoundConstructor public ReadScmFileStep(SCMSource scm, String version, String path) {
        this.scm = scm;
        this.version = version;
        this.path = path;
    }

    public SCMSource getScm() {
        return scm;
    }

    public String getVersion() {
        return version;
    }

    public String getPath() {
        return path;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static final class Execution extends SynchronousNonBlockingStepExecution<String> {

        private final transient ReadScmFileStep step;

        Execution(ReadScmFileStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override protected String run() throws Exception {
            var rev = step.scm.fetch(step.version, getContext().get(TaskListener.class), getContext().get(Run.class).getParent());
            if (rev == null) {
                throw new AbortException("No such revision");
            }
            var fs = SCMFileSystem.of(step.scm, rev.getHead(), rev);
            if (fs == null) {
                throw new AbortException("Unsupported SCM");
            }
            return fs.child(step.path).contentAsString();
        }

    }

    @Extension public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "readScmFile";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, TaskListener.class);
        }

    }

}
