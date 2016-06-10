/**
 * Copyright 2015, 2016 IBM Corp. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wasdev.sample.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.openstack4j.api.OSClient.OSClientV3;
import org.openstack4j.api.storage.ObjectStorageService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.DLPayload;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.common.Payload;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.openstack.OSFactory;

/**
 * This servlet implements the /objectStorage endpoint that supports GET, POST and DELETE 
 * in order to retrieve, add/update and delete files in Object Storage, respectively.
 */
@WebServlet("/objectStorage")
public class SimpleServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	//Get these credentials from Bluemix by going to your Object Storage service, and clicking on Service Credentials:
	private static final String USERNAME = "PUT_YOUR_OBJECT_STORAGE_USERNAME_HERE";
	private static final String PASSWORD = "PUT_YOUR_OBJECT_STORAGE_PASSWORD_HERE";
	private static final String DOMAIN_ID = "PUT_YOUR_OBJECT_STORAGE_DOMAIN_ID_HERE";
	private static final String PROJECT_ID = "PUT_YOUR_OBJECT_STORAGE_PROJECT_ID_HERE";

	private ObjectStorageService authenticateAndGetObjectStorageService() {
		String OBJECT_STORAGE_AUTH_URL = "https://identity.open.softlayer.com/v3";

		Identifier domainIdentifier = Identifier.byName(DOMAIN_ID);

		System.out.println("Authenticating...");

		OSClientV3 os = OSFactory.builderV3()
				.endpoint(OBJECT_STORAGE_AUTH_URL)
				.credentials(USERNAME,PASSWORD, domainIdentifier)
				.scopeToProject(Identifier.byId(PROJECT_ID))
				.authenticate();

		System.out.println("Authenticated successfully!");

		ObjectStorageService objectStorage = os.objectStorage();

		return objectStorage;
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		ObjectStorageService objectStorage = authenticateAndGetObjectStorageService();

		System.out.println("Retrieving file from ObjectStorage...");

		String containerName = request.getParameter("container");

		String fileName = request.getParameter("file");

		if(containerName == null || fileName == null){ //No file was specified to be found, or container name is missing
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			System.out.println("Container name or file name was not specified.");
			return;
		}

		SwiftObject pictureObj = objectStorage.objects().get(containerName,fileName);

		if(pictureObj == null){ //The specified file was not found
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			System.out.println("File not found.");
			return;
		}

		String mimeType = pictureObj.getMimeType();

		DLPayload payload = pictureObj.download();

		InputStream in = payload.getInputStream();

		response.setContentType(mimeType);

		OutputStream out = response.getOutputStream();

		IOUtils.copy(in, out);
		in.close();
		out.close();

		System.out.println("Successfully retrieved file from ObjectStorage!");
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		ObjectStorageService objectStorage = authenticateAndGetObjectStorageService();

		System.out.println("Storing file in ObjectStorage...");

		String containerName = request.getParameter("container");

		String fileName = request.getParameter("file");

		if(containerName == null || fileName == null){ //No file was specified to be found, or container name is missing
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			System.out.println("File not found.");
			return;
		}

		final InputStream fileStream = request.getInputStream();

		Payload<InputStream> payload = new PayloadClass(fileStream);

		objectStorage.objects().put(containerName, fileName, payload);
		
		System.out.println("Successfully stored file in ObjectStorage!");
	}

	private class PayloadClass implements Payload<InputStream> {
		private InputStream stream = null;

		public PayloadClass(InputStream stream) {
			this.stream = stream;
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}

		@Override
		public InputStream open() {
			return stream;
		}

		@Override
		public void closeQuietly() {
			try {
				stream.close();
			} catch (IOException e) {
			}
		}

		@Override
		public InputStream getRaw() {
			return stream;
		}

	}

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		ObjectStorageService objectStorage = authenticateAndGetObjectStorageService();

		System.out.println("Deleting file from ObjectStorage...");

		String containerName = request.getParameter("container");

		String fileName = request.getParameter("file");

		if(containerName == null || fileName == null){ //No file was specified to be found, or container name is missing
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			System.out.println("File not found.");
			return;
		}

		ActionResponse deleteResponse = objectStorage.objects().delete(containerName,fileName);

		if(!deleteResponse.isSuccess()){
			response.sendError(deleteResponse.getCode());
			System.out.println("Delete failed: " + deleteResponse.getFault());
			return;
		}
		else{
			response.setStatus(HttpServletResponse.SC_OK);
		}

		System.out.println("Successfully deleted file from ObjectStorage!");
	}

}
