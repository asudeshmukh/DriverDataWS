package com.adeona.ws;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.DriverManager;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.jboss.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.adeona.orm.Employee;
import com.adeona.orm.EmployeeCheckInOut;
import com.adeona.orm.InvoiceNumberRule;
import com.adeona.orm.OrgBase;
import com.adeona.orm.Organisation;
import com.adeona.orm.OutgoingBrokerEvents;
import com.adeona.orm.OutgoingBrokerTransactions;
import com.adeona.orm.SMSMessageStore;
import com.adeona.orm.ServiceProviderApp;
import com.adeona.orm.Trip;
import com.adeona.orm.Vendor;
import com.google.gson.Gson;
import com.gt.util.GTCommon;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;


public class AdCommon {
	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("odocon");
	static Logger logger = Logger.getLogger(AdCommon.class);
	static Properties props = null;

	private static String brokerwsURL = null;
	private static String brokerwsUserName = null;
	private static String brokerwsPassword = null;
	
	public static Properties getPropFromDriverAppWS() {
		System.out.println("inside adcomon");
		String filename = "DriverData.props";
		String filename1 = "/usr/apache-tomcat8/lib/DriverData.props";

		try {
			File f = new File(filename);
			File f1 = new File(filename1);
			if (f.exists()) {  
				props = new Properties();  
				FileInputStream inStream = new FileInputStream(f);
				props.load(inStream);
				logger.info("loaded application properties from file: " + filename);  
			}
			else if (f1.exists()){
				props = new Properties();  
				FileInputStream inStream = new FileInputStream(f1);
				props.load(inStream);
				logger.info("loaded application properties from file: " + filename1);
			}
			else {
				logger.info("No properties file found at location : " + filename1);
			}
		} catch (IOException e1) {
			/// TODO Auto-generated catch block
			logger.error("Exception " + e1.getMessage(), e1);
		}  
		logger.info("Props----------------------------"+props);
		return props;

	}

	public static String getUrlName() {
		Properties props =getPropFromDriverAppWS();
		String urlName = (String) props.get("portal");
		return urlName;
	}

	public static boolean sendEmail(String toAddr, String subject, String body,String fileNameofAttachment, List<String> receiptsName) {

		boolean success = false;

		try {
			/**
			 * Get properties from DriverData.prop file.
			 */

			Properties prop = getPropFromDriverAppWS();
			String protocol = prop.getProperty("mail.transport.protocol");
			String host = prop.getProperty("mail.smtp.host");
			String port = prop.getProperty("mail.smtp.port");
			String socketfactory = prop.getProperty("mail.smtp.socketFactory.port");
			String emailUser = prop.getProperty("emailUser");
			//			String filename = (String) prop.get("writereportLocation");

			Properties props = new Properties();
			props.put("mail.transport.protocol", protocol);
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.port", port);
			props.put("mail.smtp.socketFactory.port", socketfactory);       

			Session session = Session.getInstance(props);


			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(emailUser));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(toAddr));
			message.setSubject(subject);

			BodyPart messageBodyPart1 = new MimeBodyPart();
			messageBodyPart1.setText(body);

			MimeBodyPart messageBodyPart2 = new MimeBodyPart();

			DataSource source = new FileDataSource(fileNameofAttachment);
			messageBodyPart2.setDataHandler(new DataHandler(source));
			messageBodyPart2.setFileName(fileNameofAttachment);

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart1);
			multipart.addBodyPart(messageBodyPart2);

			//Following source code for attache the receipts to email if present.

			if(receiptsName != null && receiptsName.size() > 0){

				for(String fileNameOfAttachment : receiptsName){

					logger.info("--------------Other Attachment Name: "+fileNameOfAttachment);

					MimeBodyPart messageBodyPart = new MimeBodyPart();

					DataSource dataSource = new FileDataSource(fileNameOfAttachment);
					messageBodyPart.setDataHandler(new DataHandler(dataSource));
					messageBodyPart.setFileName(fileNameOfAttachment);

					multipart.addBodyPart(messageBodyPart);

				}

			}
			//End

			message.setContent(multipart);

			Transport.send(message);

			logger.info("-------- Message sent ----------");

			success = true;

			//    			}
			//    		}


		} catch (Exception e) {
			logger.error("Exception " + e.getMessage(), e);
		} 

		return success;

	}

	public static boolean sendSPEmail(String toAddr, String subject, String body,String fileNameofAttachment, List<String> receiptsName, OrgBase orgBase,EntityManager em) {

		boolean success = false;

		try {
			/**
			 * Get properties from DriverData.prop file.
			 */

			String protocol = "smtp";
			String host = orgBase.getFromEmailHost();
			String port = orgBase.getFromEmailPort();
			String socketfactory = orgBase.getFromEmailPort();
			String emailUser = orgBase.getFromEmailUser();
			String fromUser = orgBase.getFromEmailUser();
			String fromPassword = orgBase.getFromEmailPassword();
			String fromEmailId = orgBase.getFromEmailId();

			if ((host == null || (host.isEmpty()) || (port == null ) || (port.isEmpty()) || (emailUser == null) || (emailUser.isEmpty() ))) {

              logger.info("SMTP Params not available for base "+orgBase.getBaseId() +" sending by using primary base");
				
				OrgBase primaryBase = getPrimaryBaseByOrgId(orgBase.getOrg().getOrganisationId(),em);
				
				if (primaryBase != null ){
					protocol 		= 	"smtp";
					host 			= 	primaryBase.getFromEmailHost();
					port 			= 	primaryBase.getFromEmailPort();
					socketfactory 	= 	primaryBase.getFromEmailPort();
					emailUser		= 	primaryBase.getFromEmailUser();
					fromPassword = primaryBase.getFromEmailPassword();
					fromEmailId = primaryBase.getFromEmailId();
				}
				
				if ((host == null || (host.isEmpty()) || (port == null ) || (port.isEmpty()) || (emailUser == null) || (emailUser.isEmpty() ))) {
					logger.info("sp SMPTP properties not found sending emails with default ODOCON props");
					sendEmail(toAddr, subject, body, fileNameofAttachment, receiptsName);
					return true;
					
				}
				
			}
			Properties props = new Properties();
			props.put("mail.transport.protocol", protocol);
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.port", port);
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.socketFactory.port", socketfactory);
			
			final String user = fromUser;
			final String passWord = fromPassword;
			Session session = Session.getInstance(props,
					new javax.mail.Authenticator() {

				protected PasswordAuthentication 
				getPasswordAuthentication() {
					
					return new PasswordAuthentication(user, passWord);
				}
			});


			Message message = new MimeMessage(session);
			message.setFrom(new InternetAddress(fromEmailId));
			message.setRecipients(Message.RecipientType.TO,
					InternetAddress.parse(toAddr));
			message.setSubject(subject);

			BodyPart messageBodyPart1 = new MimeBodyPart();
			messageBodyPart1.setText(body);

			MimeBodyPart messageBodyPart2 = new MimeBodyPart();

			DataSource source = new FileDataSource(fileNameofAttachment);
			messageBodyPart2.setDataHandler(new DataHandler(source));
			messageBodyPart2.setFileName(fileNameofAttachment);

			Multipart multipart = new MimeMultipart();
			multipart.addBodyPart(messageBodyPart1);
			multipart.addBodyPart(messageBodyPart2);

			//Following source code for attache the receipts to email if present.

			if(receiptsName != null && receiptsName.size() > 0){

				for(String fileNameOfAttachment : receiptsName){

					logger.info("--------------Other Attachment Name: "+fileNameOfAttachment);

					MimeBodyPart messageBodyPart = new MimeBodyPart();

					DataSource dataSource = new FileDataSource(fileNameOfAttachment);
					messageBodyPart.setDataHandler(new DataHandler(dataSource));
					messageBodyPart.setFileName(fileNameOfAttachment);

					multipart.addBodyPart(messageBodyPart);

				}

			}
			//End

			message.setContent(multipart);

			Transport.send(message);

			logger.info("-------- Message sent ----------");

			success = true;

			//    			}
			//    		}


		} catch (Exception e) {
			logger.error("Exception " + e.getMessage(), e);
		} 

		return success;

	}


	public static void insertSMSInDB(String recipient, String message, String fromSMS) {
		EntityManager em;

		em = emf.createEntityManager();
		logger.info("insert sms in database------------------------");
		
		final int SMS_NEW = 0;
		if ((fromSMS == null) || fromSMS.isEmpty()) {
			fromSMS = "ODOCON";
		}
		
		try {
			em.getTransaction().begin();
			SMSMessageStore smsBean = new SMSMessageStore();
			smsBean.setRecipient(recipient);
			smsBean.setMessage(message);
			smsBean.setStatus(SMS_NEW);
			smsBean.setSource(fromSMS);
			em.persist(smsBean);
			em.getTransaction().commit();

		} catch (Exception a) {
			logger.error("Exception " + a.getMessage());
			logger.error(GTCommon.getStackTraceAsString(a));

		}
	}

	public static HashMap<Integer,String> getRuleType(){
		HashMap<Integer, String> ruleType = new HashMap<Integer, String>();
		ruleType.put(1,"GRO to GRC");
		ruleType.put(2, "GSO to GSC");
		ruleType.put(3, "GSO to GRC");
		ruleType.put(4, "GRO to GSC");
		return ruleType;
	}


	public static boolean generateInvoiceReport(long tripId,Trip trip,EmployeeCheckInOut ecio, String invGenerateFor, int paymentOption, 
													int billingRuleType, int sendRecalInvoice, EntityManager em) throws IOException{
		boolean isSuccess=false;
		String response, stateName = "";
		try{
			
			/**
			 * System.setProperty("java.awt.headless", "true"); 
			 * The Sun AWT classes on Unix and Linux have a dependence on the X Window System. 
			 * When you use these classes, they expect to load X client libraries and be able to 
			 * talk to an X display server.
			 */
			System.setProperty("java.awt.headless", "true"); 
			
			//			props = AdCommon.getPropFromDriverAppWS();
			String dbName = (String) props.get("dbName");
			String dbUser = (String) props.get("dbUser");
			String dbPassword = (String) props.get("dbPassword");
			String dbServer = (String) props.get("dbServer");
			String dbConnectString= "jdbc:mysql://"+dbServer+"/"+dbName+"?user="+dbUser+"&password="+dbPassword;
			String db_driver=props.getProperty("DB_DRIVER_AD");
			String outreportLocation = (String) props.get("writereportLocation");
			String readreportLocation=props.getProperty("readreportLocation");

			//If old file present the move it to old folder.
			String res = moveOldInvoiceReport(String.valueOf(tripId));
			logger.info("-----response from moveOldInvoiceReport: "+res);
			// Get jasper report
			//				String jrxmlFileName = readreportLocation+"/Invoicereport1.jrxml";
			String jasperFileName = readreportLocation+"/Invoicereport1.jasper";
			String pdfFileName = outreportLocation+"/inv_"+tripId+"_"+System.currentTimeMillis()+".pdf";

			final HashMap parameters = new HashMap();
			String tripid=String.valueOf(tripId);
			parameters.put("tripId",tripid);
			if("retail".equalsIgnoreCase(invGenerateFor)){
				parameters.put("Name","Customer Name:");
				parameters.put("Address","Customer Address:");
			}
			else{
				parameters.put("Name","Corporate Name:");
				parameters.put("Address","Corporate Address:");
			}

			//				JasperCompileManager.compileReportToFile(jrxmlFileName, jasperFileName);

			Class.forName(db_driver);
			final Connection connection=DriverManager.getConnection(dbConnectString);

			logger.info("parameter----"+parameters);
			logger.info("Connection---"+dbConnectString);
			// Generate jasper print
			JasperPrint jprint = (JasperPrint) JasperFillManager.fillReport(jasperFileName, parameters, connection);  
			// Export pdf file
			
			JasperExportManager.exportReportToPdfFile(jprint, pdfFileName);
			logger.info("PDF file name--------------------"+pdfFileName);
			logger.info("Done exporting reports to pdf");

			List<String> receiptsName = getReceiptsOfTrip(tripid);

			isSuccess=true;
			
			if (sendRecalInvoice == -1) {// means use logic as per bug 2185 -MB
				if(paymentOption != WSConstants.BK_PATMENT_TYPE_CREDIT){
					if(billingRuleType == 0){
						sendEmailInvoiceToUser(ecio.getEmployee(), trip,pdfFileName, receiptsName, em);
						logger.info("---------- Invoice Email is send for Billing Rule Type: "+billingRuleType);
					}
					else if(billingRuleType == 2){
						sendEmailInvoiceToUser(ecio.getEmployee(), trip,pdfFileName, receiptsName, em);
						logger.info("---------- Invoice Email is send for Billing Rule Type: "+billingRuleType);
					}
					else{
						logger.info("---------- Invoice Email is not sent for Billing Rule Type: "+billingRuleType);
					}
				}
				else{
					logger.info("---------- Invoice Email is not sent for Payment Option: "+paymentOption);
				}
			} else if (sendRecalInvoice == 1) { // sendRecalInvoice =true is passed from portal , see Bug 2183 -MB
				logger.info("sendRecalInvoice is true so sending invoice email");
				sendEmailInvoiceToUser(ecio.getEmployee(), trip,pdfFileName, receiptsName, em);
			}
			
		}
		
		catch(ClassNotFoundException ce){
			logger.info("Exception in report------------"+ce,ce);
			logger.info("Exception in report generation.");
			isSuccess=false;
			response="Error";
		}
		
		catch(JRException je){
			logger.info("Exception in report------------"+je,je);
			logger.info("Exception in report generation.");
			isSuccess=false;
			response="Error";
		}
		catch(Exception e){
			logger.info("Exception in report------------"+e,e);
			logger.info("Exception in report generation.");
			isSuccess=false;
			response="Error";
		}
		return isSuccess;

	}

	//Following method is used for move the existing report file to old folder.
	private static String moveOldInvoiceReport(String tripId){
		logger.info("----------Inside moveOldInvoiceReport for tripId: "+tripId);
		String response = "", oldFileName = "";
		InputStream inStream = null;
		OutputStream outStream = null;

		try{
			Properties prop = getPropFromDriverAppWS();
			String outreportLocation = (String) prop.get("writereportLocation");

			//Step 1: Get the all files name from directory.
			File folder = new File(outreportLocation);
			File[] listOfFiles = folder.listFiles();

			if(listOfFiles.length > 0){

				for (int i = 0; i < listOfFiles.length; i++) {
					if (listOfFiles[i].isFile()) {
						String[] splitted = listOfFiles[i].getName().split("_");
						//Step 2: If tripId found in file name then break the for loop.
						if(tripId.equalsIgnoreCase(splitted[1])){
							oldFileName = listOfFiles[i].getName();
							break;
						}
					}
				}
				logger.info("----------Old File Name: "+oldFileName);

				if(oldFileName.length() > 0){

					File oldFile =new File(outreportLocation+"/"+oldFileName);
					File moveFile =new File(outreportLocation+"/old/"+oldFileName);

					inStream = new FileInputStream(oldFile);
					outStream = new FileOutputStream(moveFile);

					byte[] buffer = new byte[1024];

					int length;
					//copy the file content in bytes
					while ((length = inStream.read(buffer)) > 0){

						outStream.write(buffer, 0, length);

					}

					inStream.close();
					outStream.close();

					//delete the original file
					oldFile.delete();

					logger.info("---------- File moved successfully.");

					response = "success";

				}
				else{
					logger.info("---------- File not found.");
					response = "FileNotFound";
				}
			}
		}
		catch(Exception ex){
			logger.info("----------Exception in moveOldInvoiceReport: "+ex);
			response = "Error";
		}
		return response;

	}

	//Following method is used for get the receipt list of trip.
	public static List<String> getReceiptsOfTrip(String tripId){
		logger.info("---------- Inside getReceiptsOfTrip().");
		List<String> receiptNames = null;
		InputStream inStream = null;
		OutputStream outStream = null;
		try{

			Properties prop = getPropFromDriverAppWS();
			String receiptLocation=(String)props.getProperty("receiptLocation");
			logger.info("---------- Receipt Location Path: "+receiptLocation);

			//Step 1: Get the all files name from directory.
			File folder = new File(receiptLocation);
			File[] listOfFiles = folder.listFiles();

			if(listOfFiles.length > 0){

				receiptNames = new ArrayList<String>();

				for (int i = 0; i < listOfFiles.length; i++) {

					if (listOfFiles[i].isFile()) {
						String[] splitted = listOfFiles[i].getName().split("_");
						//Step 2: If tripId found in file name then add file name into list.
						if(tripId.equalsIgnoreCase(splitted[0])){

							logger.info("---------- Requested Trip Id: "+tripId+" and Found Trip Id: "+splitted[0]);
							receiptNames.add(receiptLocation+"/"+listOfFiles[i].getName());

						}
					}
				}

			}

		}
		catch(Exception ex){
			logger.info("----------Exception in getReceiptsOfTrip: "+ex);
		}

		return receiptNames;
	}

	public static void sendEmailInvoiceToUser(Employee emp,Trip trip,String attachmentFileName, List<String> receiptsName, EntityManager em) {

		Vendor vendor = trip.getVehicle().getVendor();
		long tripId = trip.getTripId();
		
		Organisation org = trip.getBase().getOrg();
		
		OutgoingBrokerEvents obe = CommonQueries.getOBEForOrg(org.getOrganisationId(), WSConstants.OBE_EVENT_INVOICECUSTNOTIFICATION, em);
		
		boolean isSendOdoconEmail = false;
		
		if (obe != null) {
			
			logger.info("-------- Broker web service event " + WSConstants.OBE_EVENT_INVOICECUSTNOTIFICATION + " registered --------------");
			
			if ((brokerwsURL == null) || (brokerwsUserName == null) || (brokerwsPassword == null)) {
				
				Properties props = AdCommon.getPropFromDriverAppWS();
				brokerwsURL = props.getProperty("brokerwsURL");
				brokerwsUserName = props.getProperty("brokerwsUserName");
				brokerwsPassword = props.getProperty("brokerwsPassword");
				
			}
			
			if (CommonQueries.isBWSCallValidIfLTBooking(trip, em)) {
				callBWSForInvoiceCustNotification(trip, brokerwsURL, brokerwsUserName, brokerwsPassword, org, attachmentFileName, em);
			} else {
				isSendOdoconEmail = true;
			}
			
		} else {
			isSendOdoconEmail = true;
		}
		
		if (isSendOdoconEmail) {
			ServiceProviderApp spApp = AdCommon.getServiceProviderAppBySP(trip.getBase().getOrg().getOrganisationId(), em);

            String spName = "ODOCON";
			
			if (spApp != null) {
				spName = spApp.getOrg().getName();
			}
			logger.info("-------- BWS event " + WSConstants.OBE_EVENT_INVOICECUSTNOTIFICATION + " not registered, hence sending email --------------");
			
			String emailId = emp.getEmail();
			String subject = "Invoice ";

			String body = "Dear "+emp.getName()+","+"\n\n"+"Thank you for using "+vendor.getBase().getOrg().getName()+".\n"
					+ "Hope you had a pleasant experience.\n"
					+ "Your Invoice is ready and is attached here with.\n"
					+ "We look forward to hosting you again.\n\n"
					+ "Regards.. Team "+spName;

			logger.info("Employee mail id------------"+emailId);
			logger.info("Subject----------------"+subject);
			logger.info("message---------------"+body);
			if (spApp == null) {
				AdCommon.sendEmail(emailId, subject, body,attachmentFileName, receiptsName);
			} else {
				AdCommon.sendSPEmail(emailId, subject, body,attachmentFileName, receiptsName,trip.getBase(),em);
			}
			
			
		}
		

	}

	public static void sendOTP(Trip trip,String otp,EntityManager em) {


		String mobile = "";
		String custName = trip.getBookingReq().getEmployee().getName();
		String reportingTime = trip.getReportingTime();
		String serviceCityName = trip.getBase().getCity();
		String fromDate = "";
		String dateOnly = getOnlyDate(trip.getReportingDate()); 
		if (dateOnly != null){
			fromDate = dateOnly;
		}
		try {			
			/*
			 * Send the OTP
			 */
			String sub = "";
			if (trip.getBookingReq().getEmployee().getMobile1() != null) {
				sub = trip.getBookingReq().getEmployee().getMobile1().trim();
			}

			String text = "IMPORTANT - DO NOT DELETE - Use this OTP Before Signing - OTP: " +otp+ ". "+custName.trim()+". "+fromDate+". "+reportingTime+". "+"BRN:"+trip.getBookingReq().getBookingReqNum()+"."+serviceCityName;


			//AD*** Add.Date.Time.City

			text = text.replace("%", "%25");
			text = text.replace("&", "%26");
			text = text.replace("@", "%40");
			text = text.replace("#", "%23");
			text = text.replace("$", "%24");
			String fromSMS = getFromSMSByBase(trip.getBase(), em);
			insertSMSInDB(sub, text, fromSMS);
			logger.info("SMS TO GUEST: \n"
					+ "Recipient: " + sub + "\n "
					+ "Message: " +text);

			logger.info("CUST NAME: " +custName+"\tMOBILE: " +mobile+"\t reportingTime: " +reportingTime+"\t serviceCityName: " +serviceCityName+
					"\n_fromDate: " +fromDate);
		}
		catch (Exception ex) {

			logger.info("Could Not Send SMS to Guest For: ");
			logger.info(GTCommon.getStackTraceAsString(ex));
		}

	}


	public static String generateInvoiceNumberForOrg (Organisation org, EntityManager em) {

		String invoiceNumStr = "";

		InvoiceNumberRule invNumRule = getInvNumRuleForOrg(org.getOrganisationId(), em);

		if (invNumRule != null) {

			String prefix = invNumRule.getPrefix();
			String postfix = invNumRule.getPostFix();
			long lastGenNum = invNumRule.getLastGenNum();
			int numLength = invNumRule.getNumLength();

			logger.info("----- num length : " + numLength);


			logger.info("------- lastGenNum : " + lastGenNum + " ----------");
			
			lastGenNum ++;
			
			String formatStr = "";
			
			for (int i = 0 ; i < numLength; i++) {
				formatStr = formatStr + "0";
			}
			
			DecimalFormat df = new DecimalFormat(formatStr);
			
			String lastGenNumStr = df.format(lastGenNum);
			
			int noOfDigts = getNoOfDigits(lastGenNum);
			
			if (noOfDigts > numLength) {
				invoiceNumStr = prefix + lastGenNum + postfix;
			} else {
				invoiceNumStr = prefix + lastGenNumStr + postfix;
			}

			logger.info("-------- invoiceNumStr : " + invoiceNumStr + " ---------");

//			em.getTransaction().begin();
			invNumRule.setLastGenNum(lastGenNum);
			em.merge(invNumRule);
//			em.getTransaction().commit();

		} else {

			logger.error("-------- No Invoice Number Rule present for orgId : " + org.getOrganisationId() + " ----------");

			logger.error("-------- No Invoice Number Rule present for orgId : " + org.getOrganisationId() + " creating new invoice ----------");

			String prefix = "2017/";
			String postfix = "";
			long lastGenNum = 1;
			int numLength = 5;

			logger.info("------- lastGenNum : " + lastGenNum + " ----------");

			
			
			invoiceNumStr = prefix + lastGenNum + postfix;

			logger.info("-------- invoiceNumStr : " + invoiceNumStr + " ---------");

			invNumRule = new InvoiceNumberRule();
			invNumRule.setLastGenNum(lastGenNum);
			invNumRule.setOrg(org);
			invNumRule.setPrefix(prefix);
			invNumRule.setPostFix(postfix);
			invNumRule.setNumLength(numLength);
			
			em.persist(invNumRule);


		}


		return invoiceNumStr;

	}

	public static InvoiceNumberRule getInvNumRuleForOrg (long orgId, EntityManager em) {

		InvoiceNumberRule invNumRule = null;

		try {

			String queryStr = "FROM InvoiceNumberRule where org.organisationId = " + orgId;

			Object queryResult = null;
			Query query = em.createQuery(queryStr);
			query.setMaxResults(1);
			queryResult = query.getSingleResult();

			if (queryResult != null) {
				invNumRule = (InvoiceNumberRule)queryResult;
			}

		} catch (NoResultException ne) {
			logger.info("-------- No Records found ---------");
		}

		return invNumRule;

	}

	public static int getNoOfDigits (long no) {
		
		int digits = 0;
		
		if( no == 0) {
			no=1;
		}

		while(no > 0) {
			no = no/10;
			digits++;
		}
		
		return digits;
	}
	
	public static String getOnlyDate(Date date){

		DateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

		String dateString = "";
		dateString = formatter.format(date);

		return dateString;

	}
	
	public static boolean callBrokerWebService (Trip trip, String event, String brokerwsURL, String bwsUserName, String bwsPasswd, EntityManager em) {
		
	    String brn = trip.getBookingReq().getBookingReqNum();
	    Organisation org = trip.getBase().getOrg();
	    long organisationId = org.getOrganisationId();
		
		boolean success = false;
		
		try {
			
			logger.info("------ Looking up OutgoingBrokerService table ----------");
			
			OutgoingBrokerEvents obe = CommonQueries.getOBEForOrg(organisationId, event, em);
			
			if (obe != null) {
				
				logger.info("----- Event : " + event + " is registered for organisationid : " + organisationId + " ---------");
				logger.info("----- Calling broker web service --------");
				
				if ((brokerwsURL != null) && (!brokerwsURL.isEmpty())) {
					
					String wsURL = null;
					Gson gson = new Gson();
					
					if (event.equals(WSConstants.OBE_EVENT_INVOICE)) {
						
						String methodName = "sendInvoice";
						String queryParam1 = "inputData";
						BwsSendInvoiceBean bwsBean = new BwsSendInvoiceBean(brn, org.getOrganisationId(), trip.getTripId());
						String jsonInput = gson.toJson(bwsBean);
						
						wsURL = brokerwsURL.replace("http:", "") + methodName + "?" + queryParam1 + "=" + jsonInput;
						
					} else if (event.equals(WSConstants.OBE_EVENT_OTPCUSTNOTIFICATION)) {
						
						String methodName = "sendOTPNotification";
						String queryParam1 = "inputData";
						OTPCustNotificationBean otpCustNotification = new OTPCustNotificationBean(trip.getTripId());
						String jsonInput = gson.toJson(otpCustNotification);
						
						wsURL = brokerwsURL.replace("http:", "") + methodName + "?" + queryParam1 + "=" + jsonInput;
					}
					
					logger.info("------- wsUrl ------------");
					
					String authString = bwsUserName + ":" + bwsPasswd;
					
					String responseStr = getBulkDataFromWs(wsURL, authString);								
					
					logger.info("--------- response str : " + responseStr + " ---------");
					
					JSONParser jsonparser = new JSONParser();
					JSONObject responseJSON = (JSONObject)jsonparser.parse(responseStr);
					
					String responseCode = (String)responseJSON.get("responseCode");
					String responseDescription = (String)responseJSON.get("responseDescription"); 
					
					// Adding OutgoingBrokerTransactions Entity
					
					OutgoingBrokerTransactions obt = createOutgoingBrokerTransactionsEntity(org, "http:" + wsURL, responseCode, responseDescription);
					
					em.getTransaction().begin();
					em.persist(obt);
					em.getTransaction().commit();
					
					
				} else {
					logger.info("---------- brokerwsURL property not found in props file ------------");
				}
				
				
			} else {
				logger.info("-------- Event : " + event + " is not registered for organisationId : " + organisationId + " ------------");
			}
			
			
		} catch (Exception e) {
			logger.info("Exception !", e);
		}
		
		
		return success;
		
	}
	
	public static String getBulkDataFromWs(String urlPath, String authString) {

		String authStringEnc = new String(Base64Encoder.encode(authString
				.getBytes()));

		URL url = null;
		try {
			
			logger.info("-------------- URL Path Before constructing to URI: " + urlPath);
			
			URI uri = new URI("http", urlPath, null);
			url = uri.toURL();
			
			logger.info("---------------- URL path from URI :" + url);

		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		StringBuffer sb = new StringBuffer();
		URLConnection urlConnection;
		HttpURLConnection htcon = null;
		BufferedReader is = null;

		try {
			urlConnection = url.openConnection();
			urlConnection.setRequestProperty("Authorization", "Basic "
					+ authStringEnc);
			urlConnection.setConnectTimeout(60000);
			urlConnection.setReadTimeout(60000);

			htcon = (HttpURLConnection) urlConnection;
	        int responseCode =  htcon.getResponseCode();
            
	        int len = htcon.getContentLength();
	        logger.info("------------------------- Conn length "+len);
	        
	        if (responseCode == 200) {
			
				is = new BufferedReader(new InputStreamReader((htcon.getInputStream())));				
				int cnt =0;
				String inpData = "";
				while ((inpData = is.readLine()) != null) {
					sb.append(inpData);
					cnt++;
					logger.info("XXX: cnt=" + cnt + " bufsize="+sb.length());					
				}
	        	
	        } else if ( (responseCode == 401) || (responseCode == -1)) {
	        	
	        	logger.info("XXX: Unauthorized access: "+responseCode);
	        }
	        

			logger.info("XXX: " + sb.toString());
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (htcon != null) {
				htcon.disconnect();
			}
		}

		return sb.toString();

	}// end of bulk data
	
	
	public static OutgoingBrokerTransactions createOutgoingBrokerTransactionsEntity(Organisation org, String requestedURL, String responseCode, String responseDescription) {
		
		OutgoingBrokerTransactions obt = new OutgoingBrokerTransactions();
		obt.setOrg(org);
		obt.setRequestedURL(requestedURL);
		obt.setResponseCode(responseCode);
		obt.setResponseDescription(responseDescription);
		
		return obt;
		
	}

	public static List<Trip> getDriverActiverTrip(long driverId, EntityManager em){
		List<Trip> tripList = null;

		try {
			String tripQuery = "From Trip where driverId = "+driverId +" and GRO is not NULL and GRC is NULL "+" and status="+WSConstants.TRIP_STAUS_ACTIVE;
			logger.info("------------- getDriverActiverTrip -------- query "+tripQuery);
			Query query=em.createQuery(tripQuery);
			tripList = (List<Trip>) query.getResultList();
			//logger.info("Vehicle :"+dvassoc.getVehicle().getVehicleId());

		}catch(NoResultException e){
			logger.info("No active trip found for driver id "+driverId);

		}
		catch (Exception e) {
			logger.error("Exception in getDriverActiverTrip :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return tripList;

	}	
	public static void callDirectUrl(String directUrl){	


		try {
			URL url = new URL(directUrl);
			logger.info("URL-------------"+url);
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			int responseCode = urlConnection.getResponseCode();
			logger.info("responce code---"+responseCode);
			logger.info("Direct URL successfully called for Alert refresh.....");
		} 
		catch(MalformedURLException me)
		{
			logger.error("Normal Exception:"+me);
			logger.error(GTCommon.getStackTraceAsString(me));
		} 
		catch (IOException e) {
			logger.info("Unsuccessfully calling of direct alert refresh");
			logger.error("Exception for direct url "+ e.getMessage() );
			logger.error(GTCommon.getStackTraceAsString(e));
			//e.printStackTrace();
		}

		catch(Exception e1){
			logger.error("Normal Exception:"+e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
		}
	}
	public static String createDirectUrl(String server){
			String  url = server+"/odocon/dataUpdate?Entity=Alert";
			return url;
		
	}
	
	private static boolean callBWSForInvoiceCustNotification (Trip trip, String brokerwsURL, String bwsUserName, String bwsPasswd, Organisation org, String pdfFileName, EntityManager em) {
		
		boolean success = false;
		
		try {
			
			logger.info("----- Calling broker web service --------");
			
			if ((brokerwsURL != null) && (!brokerwsURL.isEmpty())) {
				
				String wsURL = null;
				Gson gson = new Gson();
				
				String methodName = "sendInvoiceNotification";
				String queryParam1 = "inputData";
				BwsInvoiceCustNotificationBean bwsBean = new BwsInvoiceCustNotificationBean(pdfFileName, trip.getTripId());
				String jsonInput = gson.toJson(bwsBean);
				
				wsURL = brokerwsURL.replace("http:", "") + methodName + "?" + queryParam1 + "=" + jsonInput;
				
				logger.info("------- wsUrl ------------");
				
				String authString = bwsUserName + ":" + bwsPasswd;
				
				String responseStr = AdCommon.getBulkDataFromWs(wsURL, authString);								
				
				logger.info("--------- response str : " + responseStr + " ---------");
				
				JSONParser jsonparser = new JSONParser();
				JSONObject responseJSON = (JSONObject)jsonparser.parse(responseStr);
				
				String responseCode = (String)responseJSON.get("responseCode");
				String responseDescription = (String)responseJSON.get("responseDescription"); 
				
				// Adding OutgoingBrokerTransactions Entity
				
				OutgoingBrokerTransactions obt = AdCommon.createOutgoingBrokerTransactionsEntity(org, "http:" + wsURL, responseCode, responseDescription);
				
				em.getTransaction().begin();
				em.persist(obt);
				em.getTransaction().commit();
				
				
			} else {
				logger.info("---------- brokerwsURL property not found in props file ------------");
			}
			
		} catch (Exception e) {
			logger.info("Exception !", e);
		}
		
		
		return success;
		
	}
	
	public static ServiceProviderApp getServiceProviderAppBySP(long spId ,EntityManager em){

		ServiceProviderApp spApp = null;

		try {

			String queryStr = "FROM ServiceProviderApp where organisationId = " + spId;

			Object queryResult = getHQLQuerySingleResult(queryStr, em);

			if (queryResult != null) {
				spApp = (ServiceProviderApp)queryResult;
			}

		} catch (NoResultException ne) {
			logger.info("-------- No Records found ---------");
		}


		return spApp;

	}
	public static Object getHQLQuerySingleResult (String queryStr, EntityManager em) {

		Object queryResult = null;
		Query query = em.createQuery(queryStr);
		query.setMaxResults(1);
		queryResult = query.getSingleResult();
		return queryResult;

	}
	public static OrgBase getPrimaryBaseByOrgId(long orgId, EntityManager em) {

		OrgBase ob = null;

		try {

			String queryStr = "FROM OrgBase where org.organisationId = " + orgId +" and isPrimary = true";			

			Object queryResult = getHQLQuerySingleResult(queryStr, em);

			if (queryResult != null) {
				ob = (OrgBase) queryResult;
			}

		} catch (NoResultException e) {
			logger.error("----------- No Records found -------------");

		}

		return ob;
	}
	public static String getFromSMSByBase(OrgBase orgBase, EntityManager em) {

		if ((orgBase.getFromSMS() != null) && (!orgBase.getFromSMS().isEmpty())) {
			return orgBase.getFromSMS();
		} else {

			OrgBase primaryBase = getPrimaryBaseByOrgId(orgBase.getOrg().getOrganisationId(), em);

			if ((primaryBase != null) && (primaryBase.getFromSMS() != null) && !(primaryBase.getFromSMS().trim().isEmpty())) {
				return primaryBase.getFromSMS();
			}

		}

		return "ODOCON";

	}
	public static boolean isFutureTrip(Date reportingDate, Date curDate) {

		if ((reportingDate == null) || (reportingDate == null)) {
			return true;
		}

		if (setTimeToMidnight(reportingDate) == setTimeToMidnight(curDate) || (reportingDate.getTime() < curDate.getTime())){

			return false;

		} else {

			return true;

		}


	}
	public static long setTimeToMidnight(Date date) {

		Calendar calendar = Calendar.getInstance();

		calendar.setTime( date );
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		return calendar.getTimeInMillis();

	}
}
