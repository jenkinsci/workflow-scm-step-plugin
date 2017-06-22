/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import com.google.common.collect.ImmutableSet;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A step which uses some kind of {@link SCM}.
 */
public abstract class SCMStep extends Step {

    private boolean poll = true;
    private boolean changelog = true;

    public boolean isPoll() {
        return poll;
    }
    
    @DataBoundSetter public void setPoll(boolean poll) {
        this.poll = poll;
    }
    
    public boolean isChangelog() {
        return changelog;
    }

    @DataBoundSetter public void setChangelog(boolean changelog) {
        this.changelog = changelog;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new StepExecutionImpl(this, context);
    }

    protected abstract @Nonnull SCM createSCM();

    public static final class StepExecutionImpl extends SynchronousNonBlockingStepExecution<Map<String,String>> {

        private transient final SCMStep step;

        StepExecutionImpl(SCMStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Map<String,String> run() throws Exception {
            StepContext ctx = getContext();
            Run<?, ?> run = ctx.get(Run.class);
            step.checkout(run, ctx.get(FilePath.class), ctx.get(TaskListener.class), ctx.get(Launcher.class));
            Map<String,String> envVars = new TreeMap<>();
            step.createSCM().buildEnvironment(run, envVars);
            return envVars;
        }

        private static final long serialVersionUID = 1L;
    }

    public final void checkout(Run<?,?> run, FilePath workspace, TaskListener listener, Launcher launcher) throws Exception {
            File changelogFile = null;
            if (changelog) {
                for (int i = 0; ; i++) {
                    changelogFile = new File(run.getRootDir(), "changelog" + i + ".xml");
                    if (!changelogFile.exists()) {
                        break;
                    }
                }
            }
            SCM scm = createSCM();
            SCMRevisionState baseline = null;
            Run<?,?> prev = run.getPreviousBuild();
            if (prev != null) {
                MultiSCMRevisionState state = prev.getAction(MultiSCMRevisionState.class);
                if (state != null) {
                    baseline = state.get(scm);
                }
            }
            scm.checkout(run, launcher, workspace, listener, changelogFile, baseline);
            SCMRevisionState pollingBaseline = null;
            if (poll || changelog) {
                pollingBaseline = scm.calcRevisionsFromBuild(run, workspace, launcher, listener);
                if (pollingBaseline != null) {
                    MultiSCMRevisionState state = run.getAction(MultiSCMRevisionState.class);
                    if (state == null) {
                        state = new MultiSCMRevisionState();
                        run.addAction(state);
                    }
                    state.add(scm, pollingBaseline);
                }
            }
            for (SCMListener l : SCMListener.all()) {
                l.onCheckout(run, scm, workspace, listener, changelogFile, pollingBaseline);
            }
            scm.postCheckout(run, launcher, workspace, listener);
    }

    public static abstract class SCMStepDescriptor extends StepDescriptor {

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class, Launcher.class);
        }

    }

}
