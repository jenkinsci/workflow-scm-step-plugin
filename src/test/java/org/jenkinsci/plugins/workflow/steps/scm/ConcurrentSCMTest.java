package org.jenkinsci.plugins.workflow.steps.scm;


import hudson.model.Run;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class ConcurrentSCMTest {
  private Run run;
  private File rootDir;

  @Before
  public void setup() throws Exception {
    run = mock(Run.class);
    rootDir = Files.createTempDirectory(null).toFile();
    when(run.getRootDir()).thenReturn(rootDir);
  }

  @After
  public void cleanup() throws Exception {
    FileUtils.deleteQuietly(rootDir);
  }

  @Issue("JENKINS-39968")
  @Test public void concurrent_checkout_write_in_different_changelog_file() throws Exception {

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch ready = new CountDownLatch(2);
    SCM scmFoo = spy(NullSCM.class);
    SCM scmBar = spy(NullSCM.class);

    final File[] changelogFiles = new File[2];

    doAnswer(invocation -> {
      changelogFiles[0] = invocation.getArgumentAt(4, File.class);
      return null;
    }).when(scmFoo).checkout(any(), any(), any(), any(), any(), any());

    doAnswer(invocation -> {
      changelogFiles[1] = invocation.getArgumentAt(4, File.class);
      return null;
    }).when(scmBar).checkout(any(), any(), any(), any(), any(), any());

    SCMStep stepFoo = new GenericSCMStep(scmFoo);
    SCMStep stepBar = new GenericSCMStep(scmBar);

    Thread t1 = new Thread(() -> {
      try {
        ready.countDown();
        start.await();
        stepFoo.checkout(run, null, null, null);
      } catch (Exception ignored) {}
    });

    Thread t2 = new Thread(() -> {
      try {
        ready.countDown();
        start.await();
        stepBar.checkout(run, null, null, null);
      } catch (Exception ignored) {}
    });

    t1.start();
    t2.start();

    ready.await(); // wait for threads to be ready
    start.countDown(); // let threads finish their work
    t1.join();
    t2.join();

    Assert.assertNotEquals(changelogFiles[0], changelogFiles[1]);

  }
}