/**
 * 
 */
package org.yocto.sdk.remotetools;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.rse.core.IRSESystemType;
import org.eclipse.rse.core.RSECorePlugin;
import org.eclipse.rse.core.model.IHost;
import org.eclipse.rse.core.model.ISystemRegistry;
import org.eclipse.rse.core.subsystems.ISubSystem;
import org.eclipse.rse.services.IService;
import org.eclipse.rse.services.files.IFileService;
import org.eclipse.rse.subsystems.files.core.servicesubsystem.IFileServiceSubSystem;
import org.eclipse.rse.subsystems.terminals.core.ITerminalServiceSubSystem;
import org.yocto.sdk.remotetools.actions.IBaseConstants;

public class RSEHelper {
	
	public static IHost getRemoteConnectionByName(String remoteConnection) {
		if (remoteConnection == null)
			return null;
		IHost[] connections = RSECorePlugin.getTheSystemRegistry().getHosts();
		for (int i = 0; i < connections.length; i++)
			if (connections[i].getAliasName().equals(remoteConnection))
				return connections[i];
		return null; // TODO Connection is not found in the list--need to react
		// somehow, throw the exception?

	}
	
	public static String getRemoteHostName(String remoteConnection)
	{
		final IHost host=getRemoteConnectionByName(remoteConnection);
		if(host == null)
			return null;
		else
			return host.getHostName();
	}

	public static IService getConnectedRemoteFileService(
			IHost currentConnection, IProgressMonitor monitor) throws Exception {
		final ISubSystem subsystem = getFileSubsystem(currentConnection);

		if (subsystem == null)
			throw new Exception(Messages.ErrorNoSubsystem);

		try {
			subsystem.connect(monitor, false);
		} catch (CoreException e) {
			throw e;
		} catch (OperationCanceledException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}

		if (!subsystem.isConnected())
			throw new Exception(Messages.ErrorConnectSubsystem);

		return ((IFileServiceSubSystem) subsystem).getFileService();
	}

	public static ISubSystem getFileSubsystem(IHost host) {
		if (host == null)
			return null;
		ISubSystem[] subSystems = host.getSubSystems();
		for (int i = 0; i < subSystems.length; i++) {
			if (subSystems[i] instanceof IFileServiceSubSystem)
				return subSystems[i];
		}
		return null;
	}

	public static IHost[] getSuitableConnections() {
		
		//we only get TCF connections with files&terminal subsystem
		ArrayList <IHost> filConnections = new ArrayList <IHost>(Arrays.asList(RSECorePlugin.getTheSystemRegistry()
				.getHostsBySubSystemConfigurationCategory("files"))); //$NON-NLS-1$
		
		ArrayList <IHost> terminalConnections = new ArrayList <IHost>(Arrays.asList(RSECorePlugin.getTheSystemRegistry()
				.getHostsBySubSystemConfigurationCategory("terminals")));//$NON-NLS-1$

		Iterator <IHost>iter = filConnections.iterator();
		while(iter.hasNext()){
			IHost fileConnection = iter.next();
			IRSESystemType sysType = fileConnection.getSystemType();
			//remove none TCF terminal connection
			if(sysType == null || 
					!sysType.getId().equals(IBaseConstants.TCF_TYPE_ID) || 
					!terminalConnections.contains(fileConnection)){
				iter.remove();
			}
		}
		
		return (IHost[]) filConnections.toArray(new IHost[filConnections.size()]);
	}
	
	public static void putRemoteFile(IHost connection, String localExePath, String remoteExePath,
			IProgressMonitor monitor) throws Exception {
		
		assert(connection!=null);
		monitor.beginTask(Messages.InfoUpload, 100);
		
		IFileService fileService;
		try {
			fileService = (IFileService) getConnectedRemoteFileService(
							connection,
							new SubProgressMonitor(monitor, 5));
			File file = new File(localExePath);
			Path remotePath = new Path(remoteExePath);
			if(fileService.getFile(remotePath.removeLastSegments(1).toString(), 
					remotePath.lastSegment(), 
					new SubProgressMonitor(monitor, 5)).exists()) {
				fileService.delete(remotePath.removeLastSegments(1).toString(), 
						remotePath.lastSegment(), 
						new SubProgressMonitor(monitor, 10));
			}
			fileService.upload(file, remotePath.removeLastSegments(1)
					.toString(), remotePath.lastSegment(), true, null, null,
					new SubProgressMonitor(monitor, 80));
			// Need to change the permissions to match the original file
			// permissions because of a bug in upload
			//RemoteApplication p = remoteShellExec(
			//		config,
			//		"", "chmod", "+x " + spaceEscapify(remotePath.toString()), new SubProgressMonitor(monitor, 5)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			//Thread.sleep(500);
			//p.destroy();
			
		} finally {
			monitor.done();
		}
		return;
	}
	
	public static void putRemoteFileInPlugin(IHost connection, String locaPathInPlugin, String remoteExePath,
			IProgressMonitor monitor) throws Exception {
		
		assert(connection!=null);
		monitor.beginTask(Messages.InfoUpload, 100);
		
		IFileService fileService;
		try {
			fileService = (IFileService) getConnectedRemoteFileService(
							connection,
							new SubProgressMonitor(monitor, 5));
			InputStream  inputStream = FileLocator.openStream(
				    Activator.getDefault().getBundle(), new Path(locaPathInPlugin), false);
			Path remotePath = new Path(remoteExePath);
			/*
			if(!fileService.getFile(remotePath.removeLastSegments(1).toString(), 
					remotePath.lastSegment(), 
					new SubProgressMonitor(monitor, 5)).exists()) {
			}
			*/
			fileService.upload(inputStream, remotePath.removeLastSegments(1)
					.toString(), remotePath.lastSegment(), true, null,
					new SubProgressMonitor(monitor, 80));
			// Need to change the permissions to match the original file
			// permissions because of a bug in upload
			//RemoteApplication p = remoteShellExec(
			//		config,
			//		"", "chmod", "+x " + spaceEscapify(remotePath.toString()), new SubProgressMonitor(monitor, 5)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			//Thread.sleep(500);
			//p.destroy();
			
		} finally {
			monitor.done();
		}
		return;
	}
	
	public static void getRemoteFile(IHost connection, String localExePath, String remoteExePath,
			IProgressMonitor monitor) throws Exception {
		
		assert(connection!=null);
		monitor.beginTask(Messages.InfoDownload, 100);
		
		IFileService fileService;
		try {
			fileService = (IFileService) getConnectedRemoteFileService(
							connection,
							new SubProgressMonitor(monitor, 10));
			File file = new File(localExePath);
			file.deleteOnExit();
			monitor.worked(5);
			Path remotePath = new Path(remoteExePath);
			fileService.download(remotePath.removeLastSegments(1).toString(), 
					remotePath.lastSegment(),file,true, null,
					new SubProgressMonitor(monitor, 85));
			// Need to change the permissions to match the original file
			// permissions because of a bug in upload
			//RemoteApplication p = remoteShellExec(
			//		config,
			//		"", "chmod", "+x " + spaceEscapify(remotePath.toString()), new SubProgressMonitor(monitor, 5)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			//Thread.sleep(500);
			//p.destroy();
			
		} finally {
			monitor.done();
		}
		return;
	}
	
	public static ITerminalServiceSubSystem getTerminalSubSystem(
            IHost connection) {
        ISystemRegistry systemRegistry = RSECorePlugin.getTheSystemRegistry();
        ISubSystem[] subsystems = systemRegistry.getSubSystems(connection);
        for (int i = 0; i < subsystems.length; i++) {
        	if (subsystems[i] instanceof ITerminalServiceSubSystem) {
                ITerminalServiceSubSystem subSystem = (ITerminalServiceSubSystem) subsystems[i];
                return subSystem;
            }
        }
        return null;
    }
}