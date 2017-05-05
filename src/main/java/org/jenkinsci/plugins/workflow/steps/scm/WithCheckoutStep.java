/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A step which checks out some kind of {@link SCM}, and then runs a body with any environment variables contributed
 * by the {@link SCM} added to the environment.
 */
public class WithCheckoutStep extends Step implements Serializable {

    private boolean poll = true;
    private boolean changelog = true;
    private final SCM scm;

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

    public SCM getScm() {
        return scm;
    }

    @DataBoundConstructor
    public WithCheckoutStep(SCM s) {
        this.scm = s;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new StepExecutionImpl(this, context);
    }


    public final void checkout(Run<?,?> run, FilePath workspace, TaskListener listener, Launcher launcher) throws Exception {
        SCMStep.doCheckout(getScm(), isChangelog(), isPoll(), run, workspace, listener, launcher);
    }

    public static final class StepExecutionImpl extends StepExecution implements Serializable{

        private final WithCheckoutStep step;

        public StepExecutionImpl(WithCheckoutStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        public boolean start() throws Exception {
            Run<?, ?> run = getContext().get(Run.class);
            TaskListener listener = getContext().get(TaskListener.class);
            FilePath workspace = getContext().get(FilePath.class);
            Launcher launcher = getContext().get(Launcher.class);

            step.checkout(run, workspace, listener, launcher);
            Map<String,String> envVars = new TreeMap<>();
            step.scm.buildEnvironment(run, envVars);

            getContext().newBodyInvoker().
                    withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new ExpanderImpl(envVars))).
                    withCallback(BodyExecutionCallback.wrap(getContext())).
                    start();

            return false;
        }

        @Override public void stop(Throwable cause) throws Exception {
            // should be no need to do anything special (but verify in JENKINS-26148)
        }

        @Override public void onResume() {}

        private static final long serialVersionUID = 1L;
    }

    private static final class ExpanderImpl extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String,String> overrides;
        private ExpanderImpl(Map<String,String> overrides) {
            this.overrides = new HashMap<>();
            this.overrides.putAll(overrides);
        }

        @Override public void expand(@Nonnull EnvVars env) throws IOException, InterruptedException {
            env.overrideAll(overrides);
        }
    }

    @Extension
    public static class WithCheckoutStepDescriptor extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "withCheckout";
        }

        @Override
        public String getDisplayName() {
            return "Checkout SCM with environment";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, TaskListener.class, Launcher.class);
        }
    }

    private static final long serialVersionUID = 1L;
}
