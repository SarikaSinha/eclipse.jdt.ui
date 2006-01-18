/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ltk.internal.ui.refactoring.history;

import java.io.IOException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;

import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringPreferenceConstants;
import org.eclipse.ltk.core.refactoring.history.IRefactoringHistoryService;

import org.eclipse.ltk.internal.ui.refactoring.Messages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIMessages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIPlugin;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferencePageContainer;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.ui.dialogs.PropertyPage;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;
import org.eclipse.ui.preferences.IWorkingCopyManager;

import org.eclipse.ltk.ui.refactoring.history.RefactoringHistoryControlConfiguration;

import org.osgi.service.prefs.BackingStoreException;

/**
 * Property page for a project's refactoring history.
 * 
 * @since 3.2
 */
public final class RefactoringPropertyPage extends PropertyPage {

	/** Preference key for the warn delete preference */
	private static final String PREFERENCE_DO_NOT_WARN_DELETE= RefactoringUIPlugin.getPluginId() + ".do.not.warn.delete.history"; //$NON-NLS-1$;

	/** The enable history button, or <code>null</code> */
	private Button fEnableButton= null;

	/** The refactoring preference */
	private boolean fHasProjectHistory= false;

	/** The preferences working copy manager, or <code>null</code> */
	private IWorkingCopyManager fManager= null;

	/**
	 * {@inheritDoc}
	 */
	protected Control createContents(final Composite parent) {
		initializeDialogUnits(parent);

		final IPreferencePageContainer container= getContainer();
		if (container instanceof IWorkbenchPreferenceContainer)
			fManager= ((IWorkbenchPreferenceContainer) container).getWorkingCopyManager();

		final Composite composite= new Composite(parent, SWT.NONE);
		final GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		layout.marginRight= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		composite.setLayout(layout);

		final Label label= new Label(composite, SWT.WRAP);
		label.setText(RefactoringUIMessages.RefactoringPropertyPage_label_message);

		final ManageRefactoringHistoryControl control= new ManageRefactoringHistoryControl(composite, new RefactoringHistoryControlConfiguration(getCurrentProject(), true, true));
		control.createControl();
		control.getDeleteAllButton().addSelectionListener(new SelectionAdapter() {

			public final void widgetSelected(final SelectionEvent event) {
				final IProject project= getCurrentProject();
				final IPreferenceStore store= RefactoringUIPlugin.getDefault().getPreferenceStore();
				MessageDialogWithToggle dialog= null;
				if (!store.getBoolean(PREFERENCE_DO_NOT_WARN_DELETE) && !control.getInput().isEmpty()) {
					dialog= MessageDialogWithToggle.openYesNoQuestion(getShell(), RefactoringUIMessages.RefactoringPropertyPage_confirm_delete_caption, Messages.format(RefactoringUIMessages.RefactoringPropertyPage_confirm_delete_pattern, project.getName()), RefactoringUIMessages.RefactoringHistoryWizard_do_not_show_message, false, null, null);
					store.setValue(PREFERENCE_DO_NOT_WARN_DELETE, dialog.getToggleState());
				}
				if (dialog == null || dialog.getReturnCode() == IDialogConstants.YES_ID) {
					IRefactoringHistoryService service= RefactoringCore.getRefactoringHistoryService();
					try {
						service.connect();
						try {
							RefactoringCore.getRefactoringHistoryService().deleteProjectHistory(project, null);
						} catch (CoreException exception) {
							final Throwable throwable= exception.getStatus().getException();
							if (throwable instanceof IOException)
								MessageDialog.openError(getShell(), RefactoringUIMessages.ChangeExceptionHandler_refactoring, throwable.getLocalizedMessage());
							else
								RefactoringUIPlugin.log(exception);
						}
						control.setInput(service.getProjectHistory(project, null));
					} finally {
						service.disconnect();
					}
				}
			}
		});
		control.getDeleteButton().addSelectionListener(new SelectionAdapter() {

			public final void widgetSelected(final SelectionEvent event) {
				super.widgetSelected(event);
			}
		});
		fEnableButton= new Button(composite, SWT.CHECK);
		fEnableButton.setText(RefactoringUIMessages.RefactoringPropertyPage_enable_message);
		fEnableButton.setData(RefactoringPreferenceConstants.PREFERENCE_ENABLE_PROJECT_REFACTORING_HISTORY);

		fEnableButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		fEnableButton.setSelection(hasRefactoringHistory());

		new Label(composite, SWT.NONE);

		final IProject project= getCurrentProject();
		if (project != null) {
			final IRefactoringHistoryService service= RefactoringCore.getRefactoringHistoryService();
			try {
				service.connect();
				control.setInput(service.getProjectHistory(project, null));
			} finally {
				service.disconnect();
			}
		}
		applyDialogFont(composite);

		return composite;
	}

	/**
	 * Returns the project currently associated with this property page.
	 * 
	 * @return the currently associated project, or <code>null</code>
	 */
	private IProject getCurrentProject() {
		return (IProject) getElement().getAdapter(IProject.class);
	}

	/**
	 * Returns the preferences for the specified context.
	 * 
	 * @param manager
	 *            the working copy manager
	 * @param context
	 *            the scope context
	 * @return the preferences
	 */
	private IEclipsePreferences getPreferences(final IWorkingCopyManager manager, final IScopeContext context) {
		final IEclipsePreferences preferences= context.getNode(RefactoringCore.ID_PLUGIN);
		if (manager != null)
			return manager.getWorkingCopy(preferences);
		return preferences;
	}

	/**
	 * Returns whether a project has an explicit refactoring history.
	 * 
	 * @return <code>true</code> if the project contains an explicit project
	 *         history, <code>false</code> otherwise
	 */
	private boolean hasRefactoringHistory() {
		final IProject project= getCurrentProject();
		if (project != null)
			return RefactoringCore.getRefactoringHistoryService().hasProjectHistory(project);
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	protected void performDefaults() {
		super.performDefaults();
		final IProject project= getCurrentProject();
		if (project != null)
			setPreference(fManager, new ProjectScope(project), RefactoringPreferenceConstants.PREFERENCE_ENABLE_PROJECT_REFACTORING_HISTORY, null);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean performOk() {
		final IProject project= getCurrentProject();
		if (project != null)
			setPreference(fManager, new ProjectScope(project), RefactoringPreferenceConstants.PREFERENCE_ENABLE_PROJECT_REFACTORING_HISTORY, Boolean.valueOf(fEnableButton.getSelection()).toString());
		if (fManager != null)
			try {
				fManager.applyChanges();
				final IRefactoringHistoryService service= RefactoringCore.getRefactoringHistoryService();
				final boolean history= service.hasProjectHistory(project);
				if (history != fHasProjectHistory && project != null)
					service.setProjectHistory(project, history, null);
			} catch (BackingStoreException exception) {
				RefactoringUIPlugin.log(exception);
			} catch (CoreException exception) {
				RefactoringUIPlugin.log(exception);
			}
		return super.performOk();
	}

	/**
	 * Sets the preference for a certain context.
	 * 
	 * @param manager
	 *            the working copy manager
	 * @param context
	 *            the scope context
	 * @param key
	 *            the key of the preference
	 * @param value
	 *            the value of the preference
	 */
	private void setPreference(final IWorkingCopyManager manager, final IScopeContext context, final String key, final String value) {
		final IEclipsePreferences preferences= getPreferences(manager, context);
		if (value != null)
			preferences.put(key, value);
		else
			preferences.remove(key);
	}

	/**
	 * {@inheritDoc}
	 */
	public void setVisible(final boolean visible) {
		fHasProjectHistory= hasRefactoringHistory();
		super.setVisible(visible);
	}
}