/*******************************************************************************
 * Copyright (c) 2006, 2021 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   David Saff (saff@mit.edu) - initial API and implementation
 *             (bug 102632: [JUnit] Support for JUnit 4.)
 *******************************************************************************/

package org.eclipse.jdt.internal.junit4.runner;

import org.junit.runner.Description;

import org.eclipse.jdt.internal.junit.runner.ITestIdentifier;

public class JUnit4Identifier implements ITestIdentifier {
	private final Description fPlan;

	public JUnit4Identifier(Description plan) {
		this.fPlan= plan;
	}

	@Override
	public String getName() {
		return fPlan.getDisplayName();
	}

	@Override
	public int hashCode() {
		return fPlan.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (! (obj instanceof JUnit4Identifier))
			return false;

		JUnit4Identifier id= (JUnit4Identifier) obj;
		return fPlan.equals(id.fPlan);
	}

	@Override
	public String getDisplayName() {
		return getName();
	}

	@Override
	public String getParameterTypes() {
		return ""; //$NON-NLS-1$
	}

	@Override
	public String getUniqueId() {
		return ""; //$NON-NLS-1$
	}

}
