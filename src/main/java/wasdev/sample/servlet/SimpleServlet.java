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
import org.openstack4j.model.common.DLPayload;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.storage.object.SwiftObject;
import org.openstack4j.openstack.OSFactory;

/**
 * Servlet implementation class SimpleServlet
 */
@WebServlet("/getBluemixPicture")
public class SimpleServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String OBJECT_STORAGE_AUTH_URL = "https://identity.open.softlayer.com/v3";

		//Get these credentials from Bluemix by going to your Object Storage service, and clicking on Service Credentials:
		String username = "PUT_YOUR_OBJECT_STORAGE_USERNAME_HERE";
		String password = "PUT_YOUR_OBJECT_STORAGE_PASSWORD_HERE";
		String domainId = "PUT_YOUR_OBJECT_STORAGE_DOMAIN_ID_HERE";
		String projectId = "PUT_YOUR_OBJECT_STORAGE_PROJECT_ID_HERE";

		Identifier domainIdentifier = Identifier.byName(domainId);

		System.out.println("Authenticating...");
		
		OSClientV3 os = OSFactory.builderV3()
				.endpoint(OBJECT_STORAGE_AUTH_URL)
				.credentials(username,password, domainIdentifier)
				.scopeToProject(Identifier.byId(projectId))
				.authenticate();

		System.out.println("Authenticated successfully!");

		System.out.println("Retrieving picture from ObjectStorage...");
		
		SwiftObject pictureObj = os.objectStorage().objects().get("dgonzContainer","bluemix-logo.png");
		
		String mimeType = pictureObj.getMimeType();
		
		DLPayload payload = pictureObj.download();
		
		InputStream in = payload.getInputStream();
		
		response.setContentType(mimeType);
		
		OutputStream out = response.getOutputStream();
		
		IOUtils.copy(in, out);
		in.close();
		out.close();
		
		System.out.println("Successfully retrieved picture from ObjectStorage!");

	}

}
