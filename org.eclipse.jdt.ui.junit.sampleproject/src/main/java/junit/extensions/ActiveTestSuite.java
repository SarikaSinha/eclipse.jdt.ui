package junit.extensions;

/*-
 * #%L
 * org.eclipse.jdt.ui.junit.sampleproject
 * %%
 * Copyright (C) 2020 Eclipse Foundation
 * %%
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 * #L%
 */

import junit.framework.*;

/**
 * A TestSuite for active Tests. It runs each test in a separate thread and
 * waits until all threads have terminated. -- Aarhus Radisson Scandinavian
 * Center 11th floor
 */
public class ActiveTestSuite extends TestSuite {
	private volatile int fActiveTestDeathCount;

	public ActiveTestSuite() {
	}

	public ActiveTestSuite(Class theClass) {
		super(theClass);
	}

	public ActiveTestSuite(String name) {
		super(name);
	}

	public ActiveTestSuite(Class theClass, String name) {
		super(theClass, name);
	}

	public void run(TestResult result) {
		fActiveTestDeathCount = 0;
		super.run(result);
		waitUntilFinished();
	}

	public void runTest(final Test test, final TestResult result) {
		Thread t = new Thread() {
			public void run() {
				try {
					// inlined due to limitation in VA/Java
					// ActiveTestSuite.super.runTest(test, result);
					test.run(result);
				} finally {
					ActiveTestSuite.this.runFinished(test);
				}
			}
		};
		t.start();
	}

	synchronized void waitUntilFinished() {
		while (fActiveTestDeathCount < testCount()) {
			try {
				wait();
			} catch (InterruptedException e) {
				return; // ignore
			}
		}
	}

	synchronized public void runFinished(Test test) {
		fActiveTestDeathCount++;
		notifyAll();
	}
}