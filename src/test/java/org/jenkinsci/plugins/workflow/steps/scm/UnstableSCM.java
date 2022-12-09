package org.jenkinsci.plugins.workflow.steps.scm;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCMRevisionState;

import java.io.File;
import java.io.IOException;

public class UnstableSCM extends org.jvnet.hudson.test.FakeChangeLogSCM {
    private int failedCount;

    public UnstableSCM(int failedCount) {
        this.failedCount = failedCount;
    }

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath remoteDir, TaskListener listener, File changeLogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        try {
            if (failedCount > 0) {
                throw new IOException("IO Exception happens");
            }
            super.checkout(build, launcher, remoteDir, listener, changeLogFile, baseline);
        } finally {
            failedCount--;
        }
    }
}
