/*******************************************************************************
 * Copyright (c) 2010-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 *******************************************************************************/

package com.liferay.ide.eclipse.server.tomcat.ui;

import com.liferay.ide.eclipse.project.ui.ProjectUIPlugin;
import com.liferay.ide.eclipse.server.tomcat.core.ILiferayTomcatRuntime;
import com.liferay.ide.eclipse.server.tomcat.core.job.CleanAppServerJob;
import com.liferay.ide.eclipse.server.tomcat.core.util.LiferayTomcatUtil;
import com.liferay.ide.eclipse.server.util.ServerUtil;
import com.liferay.ide.eclipse.ui.action.AbstractObjectAction;

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.IRuntimeWorkingCopy;
import org.eclipse.wst.server.core.TaskModel;
import org.eclipse.wst.server.ui.internal.Messages;
import org.eclipse.wst.server.ui.internal.ServerUIPlugin;
import org.eclipse.wst.server.ui.internal.wizard.TaskWizard;
import org.eclipse.wst.server.ui.internal.wizard.WizardTaskUtil;
import org.eclipse.wst.server.ui.wizard.WizardFragment;

/**
 * @author Greg Amerson
 */
@SuppressWarnings("restriction")
public class CleanAppServerAction extends AbstractObjectAction {

	public CleanAppServerAction() {
		super();
	}

	public void run(IAction action) {
		try {
			if (!(fSelection instanceof IStructuredSelection)) {
				return;
			}

			Object elem = ((IStructuredSelection) fSelection).toArray()[0];

			if (!(elem instanceof IProject)) {
				return;
			}

			IProject project = (IProject) elem;

			IRuntime runtime = ServerUtil.getRuntime(project);

			ILiferayTomcatRuntime portalTomcatRuntime = LiferayTomcatUtil.getLiferayTomcatRuntime(runtime);

			if (portalTomcatRuntime == null) {
				return;
			}

			IStatus status = runtime.validate(new NullProgressMonitor());

			IPath bundleZipLocation = portalTomcatRuntime.getBundleZipLocation();

			if (!status.isOK() || bundleZipLocation == null || (!bundleZipLocation.toFile().exists())) {
				boolean retval =
					MessageDialog.openQuestion(
						getDisplay().getActiveShell(), getTitle(),
						"A valid bundle zip location is required for performing this action.  The runtime \"" +
							runtime.getName() +
							"\" does not have a valid zip location.\n\nDo you want to specify the location of the bundle zip file now?");

				if (retval) {
					editRuntime(runtime);

					// refresh the portalTomcatRuntime
					portalTomcatRuntime = LiferayTomcatUtil.getLiferayTomcatRuntime(runtime);

					bundleZipLocation = portalTomcatRuntime.getBundleZipLocation();

					if (bundleZipLocation == null) {
						return;
					}
					else {
						run(action);
						return;
					}
				}
				else {
					return;
				}
			}

			cleanAppServer(project);
		}
		catch (Exception ex) {
			ProjectUIPlugin.logError(ex);
		}
	}

	@Override
	public void selectionChanged(IAction action, ISelection selection) {
		super.selectionChanged(action, selection);
	}

	protected void cleanAppServer(IProject project)
		throws CoreException {

		String[] labels = new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL };

		MessageDialog dialog =
			new MessageDialog(
				getDisplay().getActiveShell(),
				getTitle(),
				null,
				"Performing this action will delete the entire tomcat directory including all configuration, data, and deployed webapps.  If you have other plugins deployed they will have to be republished.\n\nDo you wish to continue?",
				MessageDialog.WARNING, labels, 1);

		int retval = dialog.open();

		if (retval == MessageDialog.OK) {
			new CleanAppServerJob(project).schedule();
		}
	}

	protected void editRuntime(IRuntime runtime) {
		IRuntimeWorkingCopy runtimeWorkingCopy = runtime.createWorkingCopy();
		if (showWizard(runtimeWorkingCopy) != Window.CANCEL) {
			try {
				runtimeWorkingCopy.save(false, null);
			}
			catch (Exception ex) {
				// ignore
			}
		}
	}

	protected String getTitle() {
		return "Clean App Server";
	}

	protected int showWizard(final IRuntimeWorkingCopy runtimeWorkingCopy) {
		String title = Messages.wizEditRuntimeWizardTitle;
		final WizardFragment fragment2 = ServerUIPlugin.getWizardFragment(runtimeWorkingCopy.getRuntimeType().getId());
		if (fragment2 == null)
			return Window.CANCEL;

		TaskModel taskModel = new TaskModel();
		taskModel.putObject(TaskModel.TASK_RUNTIME, runtimeWorkingCopy);

		WizardFragment fragment = new WizardFragment() {

			protected void createChildFragments(List<WizardFragment> list) {
				list.add((WizardFragment) fragment2.getChildFragments().get(0));
				list.add(WizardTaskUtil.SaveRuntimeFragment);
			}
		};

		TaskWizard wizard = new TaskWizard(title, fragment, taskModel);
		wizard.setForcePreviousAndNextButtons(true);
		WizardDialog dialog = new WizardDialog(getDisplay().getActiveShell(), wizard);
		return dialog.open();
	}

}
