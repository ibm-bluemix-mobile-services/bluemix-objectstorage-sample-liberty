# Getting started with IBM Object Storage for Bluemix service using a Java server application

IBM® Object Storage for Bluemix® provides a fully distributed storage platform which you can use to easily store or back up files for your applications. [Read more about it in the documentation here.](https://console.ng.bluemix.net/docs/services/ObjectStorage/index.html) 

This sample project shows how to create REST endpoints to save and update files in Object Storage, retrieve them, and delete them using an IBM Websphere Liberty application server. In order to do so, the [OpenStack4J Java SDK](http://www.openstack4j.com/learn) will be used. However, there are several others that could also be used, [listed here](https://wiki.openstack.org/wiki/SDKs#Java). 


#Adding the OpenStack4J SDK to the project
Add the SDK in the Java Project, as explained [here](http://www.openstack4j.com/learn/getting-started). However, since this Java project is using Maven for dependency management, it can be added like this to the pom.xml:

```
<dependency>
  <groupId>org.pacesys</groupId>
  <artifactId>openstack4j</artifactId>
  <version>3.0.0</version>
</dependency>
```

This sample also uses the Apache IOUtils for some of the methods, which should also be added as a Maven dependency:

```
<dependency>
	<groupId>org.apache.directory.studio</groupId>
	<artifactId>org.apache.commons.io</artifactId>
	<version>2.4</version>
</dependency>
```


##Before Starting
This project has the necessary files and configuration to be uploaded to Bluemix as is. In order to do so, first create a [Bluemix application that has a NodeJS runtime](https://console.ng.bluemix.net/docs/runtimes/nodejs/index.html#nodejs_runtime). Once you have that, go the manifest.yml file in the root folder of this sample, and change the `name` and `host` properties to be your Bluemix application's name. Once done, you can upload this sample to your Bluemix application as described in the previous link.

Also, all of the code discussed in this Readme can be found in src/main/java/wasdev/sample/servlet/SimpleServlet.java.

##Authenticating and Getting Access to Object Storage

Before being able to do anything with ObjectStorage, the server first has to authenticate with the ObjectStorage service to get access to the files. This can be achieved by using OpenStack4J as follows. First, create variables with your service credentials, which can be retrieved from the ObjectStorage dashboard:

```
//Get these credentials from Bluemix by going to your Object Storage service, and clicking on Service Credentials:

private static final String USERNAME = "PUT_YOUR_OBJECT_STORAGE_USERNAME_HERE";
private static final String PASSWORD = "PUT_YOUR_OBJECT_STORAGE_PASSWORD_HERE";
private static final String DOMAIN_ID = "PUT_YOUR_OBJECT_STORAGE_DOMAIN_ID_HERE";
private static final String PROJECT_ID = "PUT_YOUR_OBJECT_STORAGE_PROJECT_ID_HERE";

```

(If using the sample, these variables should be changed inside SimpleServer.java).

Next, do the following to authenticate:

```
String OBJECT_STORAGE_AUTH_URL = "https://identity.open.softlayer.com/v3";

Identifier domainIdentifier = Identifier.byName(DOMAIN_ID);

System.out.println("Authenticating...");
	
OSClientV3 os = OSFactory.builderV3()
	.endpoint(OBJECT_STORAGE_AUTH_URL)
	.credentials(USERNAME,PASSWORD, domainIdentifier)
	.scopeToProject(Identifier.byId(PROJECT_ID))
	.authenticate();

System.out.println("Authenticated successfully!");

```

Once done, retrieve the ObjectStorageService from the OSClient, which can then be used to work with ObjectStorage itself:

```
ObjectStorageService objectStorage = os.objectStorage();
```

##Creating the REST endpoints

With this, create a REST endpoint on a liberty server that can handle the GET, POST and DELETE HTTP verbs, so that the POST endpoint is used to upload new files, the GET endpoint is used to retrieve them, and the DELETE will delete the file.

In the sample, a single REST endpoint is created called "objectStorage" which will handle all three verbs. Also, it will be assumed that the container name and file name will be passed as [query parameters in the URL](https://en.wikipedia.org/wiki/Query_string), for example, `https://serverUrl/objectStorage?container=containerName&file=fileName`.

##Retrieving a file from Object Storage

To retrieve a file, the doGet is written to get the container and file names from the query parameters in the request, and then uses those to retrieve either the file metadata or the file, as follows: 

```
ObjectStorageService objectStorage = authenticateAndGetObjectStorageService();
		
System.out.println("Retrieving file from ObjectStorage...");
		
String containerName = request.getParameter("container");
		
String fileName = request.getParameter("file");
		
if(containerName == null || fileName == null){ 
	//No file was specified to be found, or container name is missing
	
	response.sendError(HttpServletResponse.SC_NOT_FOUND);
	System.out.println("File not found.");
	return;
}
		
String metadataOnly = request.getParameter("metadataOnly");

if(metadataOnly != null && metadataOnly.equalsIgnoreCase("true")){
	objectStorage.objects().getMetadata(containerName, fileName);
}
else{
	
	SwiftObject fileObj = objectStorage.objects().get(containerName,fileName);
	
	if(fileObj == null){ //The specified file was not found
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
		System.out.println("File not found.");
		return;
	}
	
	String mimeType = fileObj.getMimeType();
		
	DLPayload payload = fileObj.download();
		
	InputStream in = payload.getInputStream();
		
	response.setContentType(mimeType);
		
	OutputStream out = response.getOutputStream();
		
	IOUtils.copy(in, out);
	in.close();
	out.close();
		
	System.out.println("Successfully retrieved file from ObjectStorage!");
}
```

This will have returned the file back to the caller if it found it, or a 404 if it was not found. For example, if you go to a browser and open `https://yourServerUrl/objectStorage?container=containerName&file=fileName`, it should retrieve it and display it or download it, depending on the file type.

##Uploading a file to Object Storage
To add or update a file, the doPost is written to receive the file as part of the POST body, by creating an ObjectStorage Payload object with the InputStream of the request. First, a class is created that implements the Payload interface, and that receives the input stream in the constructor:

```
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public InputStream getRaw() {
			return stream;
		}

	}
```

This class is then used in the doPost to upload the file to Object Storage:

```
ObjectStorageService objectStorage = authenticateAndGetObjectStorageService();

System.out.println("Storing file in ObjectStorage...");

String containerName = request.getParameter("container");

String fileName = request.getParameter("file");

if(containerName == null || fileName == null){ 
	//No file was specified to be found, or container name is missing
	response.sendError(HttpServletResponse.SC_NOT_FOUND);
	System.out.println("File not found.");
	return;
}

final InputStream fileStream = request.getInputStream();

Payload<InputStream> payload = new PayloadClass(fileStream);

objectStorage.objects().put(containerName, fileName, payload);
```

With this endpoint, you can do a POST request to `https://serverUrl/objectStorage?container=containerName&file=fileName` with the file contents as the body, and it will be uploaded to Object Storage. At this point, if you go to your Object Storage instance in Bluemix, you will see the file inside the specified container. Also, note that if the specified container does not exist, it will first create that container, and then upload the file inside it, without having to call any other methods.

##Deleting a File
In order to delete a file, the doDelete endpoint gives the ObjectStorageService the container and file names, and tries to delete it:

```
ActionResponse deleteResponse = objectStorage.objects().delete(containerName,fileName);

if(!deleteResponse.isSuccess()){
	response.sendError(deleteResponse.getCode());
	System.out.println("Delete failed: " + deleteResponse.getFault());
	return;
}
else{
	response.setStatus(HttpServletResponse.SC_OK);
}
```

This will return either a 200 OK indicating that it was successfully deleted, or an error response code from Object Storage.

##Conclusion

This sample shows the basic functionality of Object Storage by showing how to add, retrieve and delete a file. However, the OpenStack4J Java SDK has much more functionality with regards to managing containers, retrieving file metadata, and more. Learn more here: [http://www.openstack4j.com/learn/objectstorage](http://www.openstack4j.com/learn/objectstorage).