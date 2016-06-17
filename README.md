# Getting started with the IBM Object Storage for Bluemix service using a Java server application

IBM® Object Storage for Bluemix® provides a fully distributed storage platform that you can use to easily store or back up files for your applications. [Read more about it in the documentation here](https://console.ng.bluemix.net/docs/services/ObjectStorage/index.html).

This sample project shows how to create REST endpoints to save and update files in Object Storage, retrieve them, and delete them using an IBM Websphere® Application Server Liberty profile. To do so, the [OpenStack4J Java SDK](http://www.openstack4j.com/learn) is used. However, there are several others that could also be used, [listed here](https://wiki.openstack.org/wiki/SDKs#Java). 


## Adding the OpenStack4J SDK to the project
Add the SDK in the Java Project, as explained [here](http://www.openstack4j.com/learn/getting-started). However, because this Java project uses Maven for dependency management, it can be added to the `pom.xml` file as follows:

```
<dependency>
  <groupId>org.pacesys</groupId>
  <artifactId>openstack4j</artifactId>
  <version>3.0.0</version>
</dependency>
```

This sample also uses the Apache IOUtils for some methods, which should also be added as a Maven dependency:

```
<dependency>
	<groupId>org.apache.directory.studio</groupId>
	<artifactId>org.apache.commons.io</artifactId>
	<version>2.4</version>
</dependency>
```


##Before you begin
This project has the necessary files and configuration to be uploaded to Bluemix. To do so, first create a [Bluemix application that has a NodeJS runtime](https://console.ng.bluemix.net/docs/runtimes/nodejs/index.html#nodejs_runtime). Then, update the `manifest.yml` file in the root folder of this sample by changing the `name` and `host` properties to your Bluemix application's name. Finally, you can upload this sample to your Bluemix application as described in the previous link.

All code discussed in this Readme can be found in [`src/main/java/wasdev/sample/servlet/SimpleServlet.java`](src/main/java/wasdev/sample/servlet/SimpleServlet.java).

##Authenticating and getting access to Object Storage

Before you can do anything with Object Storage, the server must authenticate with the Object Storage service to get access to the files. You can use OpenStack4J to achieve this.

1. Create variables with your service credentials, which can be retrieved from the Object Storage dashboard:

```
//Get these credentials from Bluemix by going to your Object Storage service, and clicking on Service Credentials:

private static final String USERNAME = "PUT_YOUR_OBJECT_STORAGE_USERNAME_HERE";
private static final String PASSWORD = "PUT_YOUR_OBJECT_STORAGE_PASSWORD_HERE";
private static final String DOMAIN_ID = "PUT_YOUR_OBJECT_STORAGE_DOMAIN_ID_HERE";
private static final String PROJECT_ID = "PUT_YOUR_OBJECT_STORAGE_PROJECT_ID_HERE";

```

**Note**: If you use the sample, these variables should be changed inside `SimpleServer.java` file.

2. Add the following code to authenticate:

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

3. Retrieve the `ObjectStorageService` object from the `OSClient`, which can then be used to work with Object Storage itself:

```
ObjectStorageService objectStorage = os.objectStorage();
```

##Creating the REST endpoints

You can create a REST endpoint on a liberty server that can handle the GET, POST, and DELETE HTTP verbs. The POST endpoint is used to upload new files. The GET endpoint is used to retrieve the files. The DELETE endpoint is used to delete the files.

In the sample, a single REST endpoint is created called `/objectStorage`, which handles all three verbs. It is also assumed that the container name and file name are passed as [query parameters in the URL](https://en.wikipedia.org/wiki/Query_string), for example, `https://serverUrl/objectStorage?container=containerName&file=fileName`.

##Retrieving a file from Object Storage

To retrieve a file, the `doGet` method is written to get the container and file names from the query parameters in the request, and then uses those names to retrieve either the file metadata or the file, as follows: 

```
ObjectStorageService objectStorage = authenticateAndGetObjectStorageService();
		
System.out.println("Retrieving file from Object Storage...");
		
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
		
	System.out.println("Successfully retrieved file from Object Storage!");
}
```

The file is returned back to the caller if it was found, or a 404 if it was not found. For example, if you go to a browser and open `https://yourServerUrl/objectStorage?container=containerName&file=fileName`, it should retrieve it and display it, or download it, depending on the file type.

##Uploading a file to Object Storage
To add or update a file, the `doPost` method is written to receive the file as part of the POST body, by creating an Object Storage Payload object with the InputStream of the request.

1. Create a class that implements the Payload interface, and that receives the input stream in the constructor:

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

2. This class is then used in the `doPost` method to upload the file to Object Storage:

```
ObjectStorageService objectStorage = authenticateAndGetObjectStorageService();

System.out.println("Storing file in Object Storage...");

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

With this endpoint, you can do a POST request to `https://serverUrl/objectStorage?container=containerName&file=fileName` with the file contents as the body, to be uploaded to Object Storage. At this point, if you go to your Object Storage instance in Bluemix, you can see the file inside the specified container. Note that if the specified container does not exist, that container is first created, and then the file is uploaded inside it, without having to call any other methods.

##Deleting a File
To delete a file, the `doDelete` method gives the `ObjectStorageService` the container and file names, and tries to delete it:

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

This method either returns a 200 OK indicating that the file was successfully deleted, or an error response code from Object Storage.

##Conclusion

This sample shows the basic functionality of Object Storage by showing how to add, retrieve, and delete a file. However, the OpenStack4J Java SDK has much more functionality with regards to managing containers, retrieving file metadata, and more. Learn more here: [http://www.openstack4j.com/learn/objectstorage](http://www.openstack4j.com/learn/objectstorage).
