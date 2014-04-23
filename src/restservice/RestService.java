
package restservice;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ejb.EJB;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpSession;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import jxl.JXLException;
import jxl.Workbook;

import org.apache.commons.io.IOUtils;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import com.ericsson.RestJPA.arquilian.IServiceEJB_TestingUnit;
import com.ericsson.entities.Account;
import com.ericsson.entities.Enquiry;
import com.ericsson.entities.Event;
import com.ericsson.entities.EventErrorTb;
import com.ericsson.jxl.WriteExcelToDataBase;
import com.ericsson.other.AllEventsErrorEvents;
import com.ericsson.other.Authentication;
import com.ericsson.other.Count;
import com.ericsson.other.Login;
import com.ericsson.other.MessageRapper;
import com.ericsson.other.NetworkCountModelCauseEvent;
import com.ericsson.other.NetworkCountSumImsiEvent;
import com.ericsson.other.NetworkMgmtEngNetworkAnalysisCount;
import com.ericsson.other.Notification;
import com.ericsson.other.Top10Failure;
import com.ericsson.service.IServiceEJB;
import com.ericsson.uploadpath.FileOp;
import com.google.gson.Gson;
//import com.googlecode.htmleasy.View;

@Path("/message")
@RequestScoped
public class RestService {
	
	@EJB
	private IServiceEJB serviceEjb;
	
	@EJB
	private IServiceEJB_TestingUnit iServiceEJB_TestingUnit;
	
	@EJB
	private WriteExcelToDataBase  serviceImport;
    
    private Gson json = new Gson();
    
    private Notification uploadNotice = new Notification();

    
	@SuppressWarnings("deprecation")
	@POST
	@Path("/uploadfile")
	@Consumes("multipart/form-data")
	public Response uploadFile(MultipartFormDataInput input,
			@CookieParam("loginservice1") Cookie username,
			@CookieParam("loginservice2") Cookie password,
			@CookieParam("loginservice3") Cookie accoutLevel) {
		
	   long startTime = System.currentTimeMillis();
	   
    	ResponseBuilder response = null;
		Account account = null;
		List<Integer> counter = null;
		
		try {
			account = serviceEjb.searchforAccountUsernameAndPassword(username.getValue(), password.getValue());
		} catch (Exception e) {
			
		}
 
    	if(account != null && username.getValue().equals(account.getUsername()) && 
    		password.getValue().equals(account.getPassword()) && accoutLevel.getValue().equals("SysAdmin")){	
    		
			FileOp fileOp = new FileOp();
			String UPLOADED_FILE_PATH = fileOp.generatePath()+ File.separator +"Upload" + File.separator;
			
			String fileName = "";
	 
			Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
			List<InputPart> inputParts = uploadForm.get("file");
	 
			for (InputPart inputPart : inputParts) {
	 
			 try {
	 
				MultivaluedMap<String, String> header = inputPart.getHeaders();
				
				fileName = getFileName(header);
				//convert the uploaded file to inputstream
				InputStream inputStream = inputPart.getBody(InputStream.class,null);
	 
				byte [] bytes = IOUtils.toByteArray(inputStream);
				new File(UPLOADED_FILE_PATH).mkdirs();
				File file = new File(UPLOADED_FILE_PATH,fileName.trim());	
		 
				if (!file.exists()) {
					file.createNewFile();
				}		
				file.setReadable(true, false);
				file.setWritable(true, false);
				file.setExecutable(true, false);
		 
				FileOutputStream fileOutputStream = new FileOutputStream(file);
				fileOutputStream.write(bytes);
				fileOutputStream.flush();
				fileOutputStream.close();
			 
				
	            try {
	            	if(!file.canRead()){
	            		uploadNotice.setNotification("File Uploaded Failed");
						uploadNotice.setNotification1("File Access Right Prevents Upload - Please Check File");
						return Response.status(500).entity(uploadNotice).type(MediaType.APPLICATION_JSON).build();
	            	}
	            	Workbook workbook = Workbook.getWorkbook(file); 
	            	
				    counter = serviceImport.sendExcelToDatabase(workbook);
				} catch (JXLException e) {
					uploadNotice.setNotification("File Uploaded Failed");
					uploadNotice.setNotification1("File Format Not Acceptable - Please Check File");
					return Response.status(500).entity(uploadNotice).type(MediaType.APPLICATION_JSON).build();
				}
	           //file.delete();
			  } catch (IOException e) {
				  uploadNotice.setNotification("File Uploaded Failed");
				  uploadNotice.setNotification1("File Format Not Acceptable - Please Check File");
				  return Response.status(500).entity(uploadNotice).type(MediaType.APPLICATION_JSON).build();
			  }
	 
			}
			
			long endTime = System.currentTimeMillis();
			long difference = endTime - startTime;
			System.out.println("Elapsed milliseconds: " + difference);
			
			uploadNotice.setNotification(fileName + " was successful Uploaded");
			Date date = new Date(TimeUnit.MILLISECONDS.toMillis(difference));
			date.setHours(0);
			DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
			uploadNotice.setNotification1(formatter.format(date));
			uploadNotice.setNotification2(String.valueOf(counter.get(0)));
			uploadNotice.setNotification3(String.valueOf(counter.get(1)));	
			
			return Response.status(200).entity(uploadNotice).type(MediaType.APPLICATION_JSON).build();
    	}else{
    		uploadNotice.setNotification("Excel File Uploaded Failed");
    		uploadNotice.setNotification1("Account Is UnAuthorized");
    		return Response.status(401).entity(uploadNotice).type(MediaType.APPLICATION_JSON).build();
    	}
	}
 
	
	
	/**
	 * header sample
	 * {	Content-Type=[excelfile.xls], 
	 * 		Content-Disposition=[form-data; name="file"; filename="filename.extension"]
	 * }
	 **/
	//get uploaded filename, is there a easy way in RESTEasy?
	private String getFileName(MultivaluedMap<String, String> header) {
 
		String[] contentDisposition = header.getFirst("Content-Disposition").split(";");
 
		for (String filename : contentDisposition) {
			if ((filename.trim().startsWith("filename"))) {
				String[] name = filename.split("=");
 
				int index = name[1].lastIndexOf("\\")+2;

				String finalFileName = name[1].substring(index, name[1].length() - 1).trim();
				return finalFileName;
			}
		}
		return "unknown";
	}
 
	
	@POST
	@Path("/register")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response registerUser(String req){
		
		ResponseBuilder response = null;
		Account account = json.fromJson(req, Account.class);
		
		try {
			String accountExist = serviceEjb.addNewAccount(account);
			 
			Notification message = new Notification();
			message.setNotification(accountExist);
			response =  Response.status(200).entity(message).type(MediaType.APPLICATION_JSON);
		} catch (Exception e) {
			System.out.println("error occured");
			Notification message = new Notification();
			message.setNotification("Search: Internal Server Error");
			response = Response.status(500).entity(message).type(MediaType.APPLICATION_JSON);
		}
		
		return response.build();		
	}
	
	@GET
	@Path("/history/{accountName}/{accounttype}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response lookupUsersHistoryRequest(
    		@PathParam("accountName") String accountName,
			@PathParam("accounttype") String accounttype, 
    		@CookieParam("loginservice1") Cookie username,
			@CookieParam("loginservice2") Cookie password,
			@CookieParam("loginservice3") Cookie accoutLevel){
		long startTime = System.currentTimeMillis();
		ResponseBuilder response = null;
		Account account = null;
		List<Enquiry> result = null;
		Notification alert = new Notification();
		MessageRapper<Enquiry> results = new MessageRapper<Enquiry>();
		
		try {
			account = serviceEjb.searchforAccountUsernameAndPassword(username.getValue(), password.getValue());
		} catch (Exception e) {
			
		}
		
		if(account != null && username.getValue().equals(account.getUsername()) && password.getValue().equals(account.getPassword())){

			try {
				result = serviceEjb.searchforEnquiriesTypeForUser(accountName, accounttype);
				results.setData(result);
			} catch (Exception e) {
				alert.setNotification("Query Not Sucessful");
				alert.setNotification1("Server Error Occured");
				return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}
			
			if(result != null){
				long endTime = System.currentTimeMillis();
				long difference = endTime - startTime;
				Date date = new Date(difference);
				date.setHours(0);
				DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
				results.setNotification1("Query Successful");
				results.setNotification2("Query Time: " + formatter.format(date));
				response = Response.ok(results, MediaType.APPLICATION_JSON);
			}else{
				alert.setNotification("Query Sucessful");
				alert.setNotification1("No Result Exist for Query");
				return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}
		}else{		
			alert.setNotification("Query Not Sucessful");
			alert.setNotification1("Account Is UnAuthorized");
			return Response.status(401).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
	
		return response.build();
	}

	
	@GET
	@Path("/customerservicerep/{custselection}/{imsi}/{startdate}/{enddate}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response serviceCustomerSericeRepRequest(
    		@PathParam("custselection") String custselection,
			@PathParam("imsi") String imsi, 
			@PathParam("startdate") String startdate,
			@PathParam("enddate") String enddate,
			@CookieParam("loginservice1") Cookie username,
			@CookieParam("loginservice2") Cookie password,
			@CookieParam("loginservice3") Cookie accoutLevel) {
		long startTime = System.currentTimeMillis();
		ResponseBuilder response = null;
		List<Event> result = null;
		Account account = null;
		Notification alert = new Notification();
		MessageRapper<Event> results = new MessageRapper<Event>();
		
		try {
			account = serviceEjb.searchforAccountUsernameAndPassword(username.getValue(), password.getValue());
		} catch (Exception e) {
			
		}
	
		if(account != null && username.getValue().equals(account.getUsername()) && password.getValue().equals(account.getPassword())){
			
				if(custselection.equalsIgnoreCase("Event ID & Cause Codes")){
					
					try {
						result = serviceEjb.searchforEventIdCauseCodeByImsi(imsi,username.getValue(),accoutLevel.getValue());
						results.setData(result);
					} catch (Exception e) {
						result = null;
						alert.setNotification("Query Not Sucessful");
						alert.setNotification1("Server Error Occured");
						return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
					}
					
				}else if(custselection.equalsIgnoreCase("All Unique Cause Codes") && imsi != null){
					try {
						result = serviceEjb.searchforDistinctEventIdCauseCodeByImsi(imsi, username.getValue(),accoutLevel.getValue());
						results.setData(result);
					} catch (Exception e) {
						result = null;
						alert.setNotification("Query Not Sucessful");
						alert.setNotification1("Server Error Occured");
						return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
					}
						
				}else if(custselection.equalsIgnoreCase("3") && imsi != null){
					try {
						result = serviceEjb.searchforCountByImsiAndDate(imsi, startdate, enddate, username.getValue(),accoutLevel.getValue());
						results.setData(result);
					} catch (Exception e) {
						result = null;
						alert.setNotification("Query Not Sucessful");
						alert.setNotification1("Server Error Occured");
						return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
					}
				}
		}else{
			alert.setNotification("Query Not Sucessful");
			alert.setNotification1("Account Is UnAuthorized");
			return Response.status(401).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
		
		if(result != null){
			long endTime = System.currentTimeMillis();
			long difference = endTime - startTime;
			Date date = new Date(difference);
			date.setHours(0);
			DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
			results.setNotification1("Query Successful");
			results.setNotification2("Query Time: " + formatter.format(date));
			response = Response.ok(results, MediaType.APPLICATION_JSON);
		}else{
			alert.setNotification("Query Sucessful");
			alert.setNotification1("No Result Exist for Query");
			return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
		
		return response.build();
	}
	

	@GET
	@Path("/networkmanagement/{netselection}/{imsi}/{startdate}/{enddate}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response networkManagementSericeRepRequest(
    		@PathParam("netselection") String netselection,
			@PathParam("imsi") String model, 
			@PathParam("startdate") String startdate,
			@PathParam("enddate") String enddate,
			@CookieParam("loginservice1") Cookie username,
			@CookieParam("loginservice2") Cookie password,
			@CookieParam("loginservice3") Cookie accoutLevel){
		
		long startTime = System.currentTimeMillis();
		List<Long> distinctImsis = null;
		List<Long> occuranceCount1 = null;
		List<Long> durationCount = null;
		Notification alert = new Notification();
		
		ResponseBuilder response = null;
		List<Event> result = null;
		List<BigInteger> occuranceCount = null;
		List<Event> allEvents = null;
		int count;
		
		if(netselection.equalsIgnoreCase("1")){
			
			try {
	
				distinctImsis = serviceEjb.searchforFailuresByImsiAndDate(startdate, enddate, username.getValue());
				occuranceCount1 = serviceEjb.searchforCountFailuresByImsiAndDate(startdate, enddate);
				durationCount = serviceEjb.searchforDurationFailuresByImsiAndDate(startdate, enddate);
				
			} catch (Exception e) {
				alert.setNotification("Query Not Sucessful");
				alert.setNotification1("Server Error Occured");
				return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}
			
			NetworkCountSumImsiEvent tempObject = new NetworkCountSumImsiEvent();
			tempObject.setDistinctImsi(distinctImsis);
			tempObject.setOccurances(occuranceCount1);
			tempObject.setDurations(durationCount);
			long endTime = System.currentTimeMillis();
			long difference = endTime - startTime;
			Date date = new Date(difference);
			date.setHours(0);
			DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
			tempObject.setNotification1("Query Successful");
			tempObject.setNotification2("Query Time: " + formatter.format(date));
			
			if(distinctImsis != null){
				response = Response.ok(tempObject, MediaType.APPLICATION_JSON);
			}else{
				alert.setNotification("Query Sucessful");
				alert.setNotification1("No Result Exist for Query");
				return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}

		}else if(netselection.equalsIgnoreCase("2")){
			
			try {
				result = serviceEjb.searchForCauseCodeEventIDByModel(model);
				occuranceCount = serviceEjb.searchCountCauseCodeOccurancesByModel(model);
			} catch (Exception e) {
				result = null;
				alert.setNotification("Query Not Sucessful");
				alert.setNotification1("Server Error Occured");
				return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}
			
			
			NetworkCountModelCauseEvent tempObject = new NetworkCountModelCauseEvent();
			tempObject.setEvents(result);
			tempObject.setOccurances(occuranceCount);
			long endTime = System.currentTimeMillis();
			long difference = endTime - startTime;
			Date date = new Date(difference);
			date.setHours(0);
			DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
			tempObject.setNotification1("Query Successful");
			tempObject.setNotification2("Query Time: " + formatter.format(date));
			
			if(result != null){
				response = Response.ok(tempObject, MediaType.APPLICATION_JSON);
			}else{
				alert.setNotification("Query Sucessful");
				alert.setNotification1("No Result Exist for Query");
				return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}

		}
		
		else if(netselection.equalsIgnoreCase("6")){
			try {
				result = serviceEjb.searchForMarketOperatorCellIDCombo();
				occuranceCount = serviceEjb.searchCountMarketOperatorCellIDcombo();
				allEvents = serviceEjb.retrieveAllEvents();
				
			} catch (Exception e) {
				result = null;
				alert.setNotification("Query Not Sucessful");
				alert.setNotification1("Server Error Occured");
				return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}
			
			count = allEvents.size();
			System.out.println("count size is: "+ count);
			
			NetworkMgmtEngNetworkAnalysisCount tempObject = new NetworkMgmtEngNetworkAnalysisCount();
			tempObject.setEvents(result);
			tempObject.setOccurances(occuranceCount);
			tempObject.setAllEventsCount(count);
			long endTime = System.currentTimeMillis();
			long difference = endTime - startTime;
			Date date = new Date(difference);
			date.setHours(0);
			DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
			tempObject.setNotification1("Query Successful");
			tempObject.setNotification2("Query Time: " + formatter.format(date));
			if(result != null){
				response = Response.ok(tempObject, MediaType.APPLICATION_JSON);
			}else{
				alert.setNotification("Query Sucessful");
				alert.setNotification1("No Result Exist for Query");
				return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
			}
		}

		return response.build();
	}
	
	
	@GET
	@Path("/supportengineer/{suppselection}/{searchVar}/{startdate}/{enddate}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response supportEngineerSericeRepRequest(
    		@PathParam("suppselection") String suppselection,
			@PathParam("searchVar") String searchVar, 
			@PathParam("startdate") String startdate,
			@PathParam("enddate") String enddate, 
			@CookieParam("loginservice1") Cookie username,
			@CookieParam("loginservice2") Cookie password,
			@CookieParam("loginservice3") Cookie accoutLevel) {
		
		long startTime = System.currentTimeMillis();
		Notification alert = new Notification();
		ResponseBuilder response = null;
		List<Event> result = null;
		List<Long> resultItems = null;
		MessageRapper<Event> results = new MessageRapper<Event>();
		MessageRapper<Long> resultRapper = new MessageRapper<Long>();

		Count count = null;
		Account account = null;
		try {
			account = serviceEjb.searchforAccountUsernameAndPassword(username.getValue(), password.getValue());
		} catch (Exception e) {
			
		}

		
		if(account != null && username.getValue().equals(account.getUsername()) && password.getValue().equals(account.getPassword())){		
		
		
				//search by failure class - search with no dates involved
				if(suppselection.equalsIgnoreCase("1")){
					
					try {
						
						result = serviceEjb.searchImsisAffectedByFailureClass(searchVar, username.getValue());
						results.setData(result);
						
					} catch (Exception e) {						
						e.printStackTrace();
						result = null;
						alert.setNotification("Query Not Sucessful");
						alert.setNotification1("Server Error Occured");
						return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
					}
					
				}else if(suppselection.equalsIgnoreCase("2")){
					//failed imsi's between two dates - dates with no search variable involved
					try {		
						resultItems = serviceEjb.searchforFailuresByImsiAndDate(startdate, enddate, username.getValue());
						resultRapper.setData(resultItems);
					} catch (Exception e) {
						resultItems = null;
						alert.setNotification("Query Not Sucessful");
						alert.setNotification1("Server Error Occured");
						return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
					}
									
					//call failures by phone model - search with dates
				}else if(suppselection.equalsIgnoreCase("3") && searchVar != null){
					try {
						result = serviceEjb.searchForFailuresByModelAndDate(searchVar, startdate, enddate, username.getValue());
						results.setData(result);
					} catch (Exception e) {
						result = null;
						alert.setNotification("Query Not Sucessful");
						alert.setNotification1("Server Error Occured");
						return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
					}
				}
		}
		
		long endTime = System.currentTimeMillis();
		long difference = endTime - startTime;
		Date date = new Date(difference);
		date.setHours(0);
		DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
		if(result != null && resultItems == null){
			results.setNotification1("Query Successful");
			results.setNotification2("Query Time: " + formatter.format(date));
			response = Response.ok(results, MediaType.APPLICATION_JSON);
		}
		else if(result == null && resultItems != null){
			resultRapper.setNotification1("Query Successful");
			resultRapper.setNotification2("Query Time: " + formatter.format(date));
			response = Response.ok(resultRapper, MediaType.APPLICATION_JSON);		
		}else{
			alert.setNotification("Query Sucessful");
			alert.setNotification1("No Result Exist for Query");
			return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
		
		return response.build();
	}
	


////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
@POST
@Path("/login")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public Response login(String request){
	
		ResponseBuilder response = null;
		Account account = null;
		Login user = json.fromJson(request, Login.class);
		Authentication auth = new Authentication();

		try {
			account = serviceEjb.searchforAccountUsernameAndPassword(user.getUsername(), user.getPassword());
		} catch (Exception e) {
			Notification alert = new Notification();
			alert.setNotification("Server Error During login");
		
			return Response.serverError().entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
			
		if(account != null && user.getUsername().equals(account.getUsername()) && user.getPassword().equals(account.getPassword())){

			if(account.getAccounttype().equalsIgnoreCase("SysAdmin")){
				auth.setUrl("http://localhost:8080/RestJPA/sysAdmin.jsp");			
			}else if(account.getAccounttype().equalsIgnoreCase("SupEng")){
				auth.setUrl("http://localhost:8080/RestJPA/supportEngineer.jsp");
			}else if(account.getAccounttype().equalsIgnoreCase("NetEng")){
				auth.setUrl("http://localhost:8080/RestJPA/NetMgmtEng.jsp");
			}else if(account.getAccounttype().equalsIgnoreCase("CustRep")){
				auth.setUrl("http://localhost:8080/RestJPA/CustServiceRep.jsp");
			}
			auth.setAccounttype(account.getAccounttype());
			auth.setUsername(account.getUsername());
			auth.setPassword(account.getPassword());	
			response = Response.ok(auth, MediaType.APPLICATION_JSON);
		}else{
			Notification message = new Notification();
			message.setNotification("Please Enter Valid Username and Password");
			return Response.status(404).entity(message).type(MediaType.APPLICATION_JSON).build();
		}
		
		return response.build();	
}
	


@GET 
@Path("/networkmanagement/4/top10ImsiFailure/{startdate}/{enddate}")
@Produces(MediaType.APPLICATION_JSON)
public Response networkManagementSericeTop10Imsi(
							@PathParam("startdate") String startdate,
							@PathParam("enddate") String enddate, 
							@CookieParam("loginservice1") Cookie username,
							@CookieParam("loginservice2") Cookie password,
							@CookieParam("loginservice3") Cookie accoutLevel){
	long startTime = System.currentTimeMillis();
	List<Top10Failure> result = null;
	Account account = null;
	Notification alert = new Notification();
	
	try {
		account = serviceEjb.searchforAccountUsernameAndPassword(username.getValue(), password.getValue());
	} catch (Exception e) {
	}
	
	if(account != null && username.getValue().equals(account.getUsername()) && username.getValue().equals(account.getPassword())){
		try {
			result = serviceEjb.searchforTop10ImsiWithFailure(startdate,enddate);
		} catch (Exception e) {
			alert.setNotification("Query Not Sucessful");
			alert.setNotification1("Server Error Occured");
			return Response.status(500).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
			
		if(result == null){
			alert.setNotification("Query  Sucessful");
			alert.setNotification1("No Result Exist for Query");
			return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
		
		MessageRapper<Top10Failure> message = new MessageRapper<Top10Failure>();
		message.setData(result);
		message.setNotification1("Query Successful");
		
		long endTime = System.currentTimeMillis();
		long difference = endTime - startTime;
		Date date = new Date(difference);
		date.setHours(0);
		DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
		message.setNotification2("Query Time: " + formatter.format(date));
		return Response.status(200).entity(message).type(MediaType.APPLICATION_JSON).build();
	}else{
		alert.setNotification("Query Not Sucessful");
		alert.setNotification1("Account Is UnAuthorized");
		return Response.status(401).entity(alert).type(MediaType.APPLICATION_JSON).build();
	}
}



// @GET
// @Path("/getpage")
// public View getPage(String login){
//	 
//	 
//	 return new View("CustServiceRep.jsp", null);
// }
 
 
 @GET  ///Mine
 @Path("/sysAdminGetEvents")
 @Produces(MediaType.APPLICATION_JSON)
 public Response sysAdminSericeRepRequest(
		 	@CookieParam("loginservice1") Cookie username,
			@CookieParam("loginservice2") Cookie password,
			@CookieParam("loginservice3") Cookie accoutLevel){
	 	
	 	long startTime = System.currentTimeMillis();
		ResponseBuilder response = null;
		List<Event> events = null;
		List<EventErrorTb> errorEvents = null;

		Account account = null;
		Notification alert = new Notification();
		AllEventsErrorEvents tempObject = new AllEventsErrorEvents();
		
		try {
			account = serviceEjb.searchforAccountUsernameAndPassword(username.getValue(), password.getValue());
		} catch (Exception e) {
		}
		
		if(account != null && username.getValue().equals(account.getUsername()) && username.getValue().equals(account.getPassword())){
			
			events = serviceEjb.retrieveAllEvents();
			errorEvents = serviceEjb.retrieveAllErrorEvents();
			
			tempObject.setEvents(events);
			tempObject.setErrorEvents(errorEvents);
		}else{
			alert.setNotification("Query Not Sucessful");
			alert.setNotification1("Account Is UnAuthorized");
			return Response.status(401).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}

		
		
		if(events != null){
			long endTime = System.currentTimeMillis();
			long difference = endTime - startTime;
			Date date = new Date(difference);
			date.setHours(0);
			DateFormat formatter = new SimpleDateFormat("HH:mm:ss:SSS");
			tempObject.setNotification1("Query Successful");
			tempObject.setNotification2("Query Time: " + formatter.format(date));
			response = Response.ok(tempObject, MediaType.APPLICATION_JSON);
		}else{
			alert.setNotification("Query Sucessful");
			alert.setNotification1("No Result Exist for Query");
			return Response.status(404).entity(alert).type(MediaType.APPLICATION_JSON).build();
		}
		
		return response.build();
	}
  
}
