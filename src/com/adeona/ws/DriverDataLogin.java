package com.adeona.ws;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jboss.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.adeona.orm.Device;
import com.adeona.orm.Driver;
import com.adeona.orm.DriverDeviceAssoc;
import com.adeona.orm.DriverVehicleAssoc;
import com.adeona.orm.Organisation;
import com.adeona.orm.OutgoingBrokerEvents;
import com.adeona.orm.OutgoingBrokerTransactions;
import com.adeona.orm.ServiceProviderApp;
import com.adeona.orm.Trip2DeviceAssoc;
import com.adeona.orm.Vehicle;
import com.google.gson.Gson;
import com.gt.util.GTCommon;

@Path("/DriverDataLogin")
public class DriverDataLogin {
	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("odocon");

	//EntityManager em;
	Logger logger = Logger.getLogger(DriverDataLogin.class);
	
	//Props
	String urlvalue = null;
	String currentVersion = null;
	boolean isComp = false;
	String appUpgradeLink = null;
	String appUpgradeMessage = null;
	String oldVersionExpiryDate = null;

	Driver driver=null;
	Device device=null;
	boolean isDeviceBusy=false;;
	boolean isDriverBusy=false;
    DriverDeviceAssoc ddassoc=null;
	DriverVehicleAssoc dvassoc=null;
	
	List<DriverDeviceAssoc> ddassocList=null;
	List<DriverVehicleAssoc> dvassocList=null;
	List<Vehicle> allvehicles = null;
	
	private static String brokerwsURL = null;
	private static String brokerwsUserName = null;
	private static String brokerwsPassword = null;
	
	public DriverDataLogin() {
		logger.info("*********************LOGIN***********************************");
		Properties prop = AdCommon.getPropFromDriverAppWS();
		//em = emf.createEntityManager();
		if (prop != null) {
			urlvalue = prop.getProperty("server");
			currentVersion = prop.getProperty("currentAppVersion");
			String compatible = prop.getProperty("isCompatible");
			isComp = (compatible.equals("Y")? true : false);
			appUpgradeLink = prop.getProperty("appUpgradeLink");
			appUpgradeMessage = prop.getProperty("appUpgradeMessage");
			oldVersionExpiryDate = prop.getProperty("oldVersionExpiryDate");
		}
		
	}

	//getting driver using mobile number
	private Driver getDriverUsingMobilNumber(String mobileNumber, EntityManager em){
		try {
			String driverUsingMobile="From Driver where mobile = "+mobileNumber+" and status='A'";			
			Query query=em.createQuery(driverUsingMobile);
			driver=(Driver) query.getSingleResult();
			logger.info("Driver :"+driver.getDriverId());

		}catch(NoResultException e){
			logger.info("Driver not found for mobile No.: "+mobileNumber);
			//logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in get Driver Using MobilNumber :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return driver;
	}



	//getting driverDeviceAssoc using driverId and deviceId 
	public DriverDeviceAssoc getDriverDeviceAssoc(String imei, EntityManager em){

		try {
			String deviceUsingimei="select * from DriverDeviceAssoc dd, Device d where dd.deviceId=d.deviceId and dd.status!="+WSConstants.DRIVERVEHICLEASSOC_INACTIVE+" and d.serialNumber="+imei;
			logger.info("assoc query --------------------------------------"+deviceUsingimei);
			Query query=em.createNativeQuery(deviceUsingimei,DriverDeviceAssoc.class);
			ddassoc=(DriverDeviceAssoc) query.getSingleResult();
			logger.info("Status of driverDEviceAssoc-----------------"+ddassoc.getStatus());
			if(ddassoc.getStatus()==1){
				isDeviceBusy=true;
			}
			logger.info("----------------is device busy------------------"+isDeviceBusy);
		}catch(NoResultException e){
			logger.info("Driver Device assoc not found.");
			ddassoc=null;
		}
		catch (Exception e) {
			logger.error("Exception in getDriverDeviceAssoc :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
			ddassoc=null;
		}
		//logger.info("ddassoc-------------------------------"+ddassoc);
		return ddassoc;
	}

	//getting driverVehicleAssoc using driverId
	public DriverVehicleAssoc getDriverVehicleAssoc(long driverId, EntityManager em){

		try {
			String deviceUsingimei="From DriverVehicleAssoc where driverId="+driverId+" and status="+WSConstants.DRIVERVEHICLEASSOC_ACTIVE;
			logger.info("assoc query --------------------------------------"+deviceUsingimei);
			Query query=em.createQuery(deviceUsingimei);
			dvassoc=(DriverVehicleAssoc) query.getSingleResult();
			isDriverBusy=true;
			logger.info("----------------is driver busy------------------"+isDriverBusy);
		}catch(NoResultException e){
			logger.info("Driver Vehicle assoc not found.");
			dvassoc=null;
		}
		catch (Exception e) {
			logger.error("Exception in getDriverVehicleAssoc :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
			dvassoc=null;
		}
		logger.info("dvassoc-------------------------------"+dvassoc);
		return dvassoc;
	}
	
	private List<DriverDeviceAssoc> getDriverDeviceAssocUsingImei(String imei, EntityManager em){

		try {
			String driverObj="select * from DriverDeviceAssoc d, Device device where d.deviceId=device.deviceId and device.serialNumber="+imei+" and d.status="+WSConstants.DRIVERVEHICLEASSOC_ACTIVE;			
			Query query=em.createNativeQuery(driverObj,DriverDeviceAssoc.class);
			logger.info("Query----------"+driverObj);
			ddassocList=(List<DriverDeviceAssoc>) query.getResultList();
			//logger.info("Driver :"+ddassoc.getDriver().getDriverId());

		}catch(NoResultException e){
			logger.error("Exception in  getDriverDeviceAssocUsingImei :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in getDriverDeviceAssocUsingImei :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return ddassocList;
	}


	private List<DriverVehicleAssoc> getDriverVehicleAssocUsingdriverId(long driverId, EntityManager em){

		try {
			String driverObj="From DriverVehicleAssoc where driverId="+driverId +" and status="+WSConstants.DRIVERVEHICLEASSOC_ACTIVE;			
			Query query=em.createQuery(driverObj);
			dvassocList=(List<DriverVehicleAssoc>) query.getResultList();
			//logger.info("Vehicle :"+dvassoc.getVehicle().getVehicleId());

		}catch(NoResultException e){
			logger.error("Exception in  getDriverVehicleAssocUsingdriverId :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in getDriverVehicleAssocUsingdriverId :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return dvassocList;
	}
	
	//Following method is used to check Driver is already login or not on the basis of DriverDeviceAssoc.
	public boolean getDriverDeviceAssocUsingDriverId(long driverId, EntityManager em){
             boolean isBusy = false;
		try {
			String deviceUsingimei="select * from DriverDeviceAssoc dd where dd.status ="+WSConstants.DRIVERVEHICLEASSOC_ACTIVE+" and dd.driverId="+driverId;
			logger.info("assoc query --------------------------------------"+deviceUsingimei);
			Query query=em.createNativeQuery(deviceUsingimei,DriverDeviceAssoc.class);
			ddassoc=(DriverDeviceAssoc) query.getSingleResult();
			logger.info("Status of driverDEviceAssoc-----------------"+ddassoc.getStatus());
			if(ddassoc.getStatus()==1){
				isBusy=true;
			}
			logger.info("----------------is driver busy------------------"+isBusy);
		}catch(NoResultException e){
			logger.info("Driver Device assoc not found.");
			ddassoc=null;
		}
		catch (Exception e) {
			logger.error("Exception in getDriverDeviceAssocUsingDriverId :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
			ddassoc=null;
		}
		//logger.info("ddassoc-------------------------------"+ddassoc.getId());
		return isBusy;
	}

	@SuppressWarnings({ "unchecked"})
	@GET
	@Path("/ValidateDriver")	
	@Consumes({"multipart/related,text/plain"})
	public String validateDriver(@QueryParam("mobileNumber")String mobileNumber) {
		
		logger.info("----------------------VALIDATE DRIVER-------------------------- mobileNumber : "+mobileNumber);
		logger.info("----------------------VALIDATE DRIVER--------------------------");
		//logger.info(">>>>>>>>>>>>>>>>"+mobileNumber + " appVer="+appVer);
		String response = "";
		String otp;
		JSONObject responsse=new JSONObject();
		String ret =null;
		
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;

		try {
			et = em.getTransaction();
			et.begin();

			driver = getDriverUsingMobilNumber(mobileNumber, em);

			if (driver == null) {
				responsse.put("message", "Driver is not registered!!");
				responsse.put("statusCode", 500);
				response = responsse.toString();
				ret = response;
			} else {
				long driverId = driver.getDriverId();
				long deviceid = CommonQueries.getDeviceIdUsingDriverId(driverId, em);
				if (deviceid == -1) {
					responsse.put("message", "No entry in driverdeviceassoc");
					responsse.put("statusCode", 500);
					response = responsse.toString();
					ret = response;
					return ret;
				}
				String imei = getImeiUsingDeviceId(deviceid, em);
				System.out.println("driverid: " + driverId + " deviceId: " + deviceid + " imei : " + imei);

				// First thing web-service do Check remove assoc file is present
				// in respected device directory or not.
				// If present delete it.
				deleteRemoveAssocFile(imei);

				device = CommonQueries.getDeviceUsingImei(imei, em);

				// Check if driver is already login or not and same for vehicle.
				ddassocList = getDriverDeviceAssocUsingImei(imei, em);
				// et.begin();
				if (ddassocList != null && ddassocList.size() > 0) {

					for (DriverDeviceAssoc dda : ddassocList) {
						dda.setStatus(WSConstants.DRIVERVEHICLEASSOC_INACTIVE);
					}
				}

				dvassocList = getDriverVehicleAssocUsingdriverId(driver.getDriverId(), em);

				if (dvassocList != null && dvassocList.size() > 0) {

					for (DriverVehicleAssoc dva : dvassocList) {
						logger.info("-------Vehicle Reg No.: " + dva.getVehicle().getRegNo());
						dva.setStatus(WSConstants.DRIVERVEHICLEASSOC_INACTIVE);
					}
				}

				logger.info("Registered!!!!!!!!!!!!!!!!!!!!!!!!!!");
				DateFormat df = new SimpleDateFormat("MMM dd,yyyy");
				String dateTemp = df.format(driver.getCreated());
				String dateStr = dateTemp.substring(Math.max(0, dateTemp.length() - 8));

				Organisation org = device.getOrgBase().getOrg();

				otp = generateOTP(driver, dateStr, org, em);
				logger.info("-------------otp-----------------------" + otp);

				responsse.put("DriverId", driverId);
				responsse.put("OTP", otp);
				responsse.put("message", "Driver is registered!!");
				responsse.put("statusCode", 200);
				response = responsse.toString();
				// try{
				ddassoc = getDriverDeviceAssoc(imei, em);
				dvassoc = getDriverVehicleAssoc(driver.getDriverId(), em);

				// }catch(Exception e){
				// logger.error(GTCommon.getStackTraceAsString(e));
				// }
				logger.info(
						"****************************************imei  " + imei + "   driver--" + driver.getMobile());
				if (ddassoc == null && dvassoc == null) {
					logger.info("<<< new generation of aasoc >>>");
					generateDriverDeviceAssoc(otp, em);
				} else if (ddassoc != null) {
					if (ddassoc.getStatus() == 2) {
						logger.info("<<< updation of assoc >>>");
						updateDriverDeviceAssoc(otp, driver);
					}
				}
				if (isDeviceBusy && isDriverBusy) {
					// et.begin();
					ddassoc.setStatus(WSConstants.DRIVER_REQUESTED);
					ddassoc.setOtp(otp);

					dvassoc.setStatus(WSConstants.DRIVERVEHICLEASSOC_INACTIVE);

				}
			}
			et.commit();
		} catch (Exception e) {
			response = "Error, Please retry";
			logger.error("Exception in  sendAlert :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			if(et.isActive()){
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		}
		finally {
			if (em.isOpen()) {
				em.close();
			}
		}
		ret=response;	
		return ret;
	}
	
	//Following method is used for delete the "removeassoc" file.
	private void deleteRemoveAssocFile(String imeiNo){
		
		logger.info("++++++++++ Inside deleteRemoveAssocFile()");
		
		String fileName = "/usr/gt/android/devices/"+imeiNo+"/removeassoc";
		
		logger.info("++++++++++ File Name: "+fileName);
		try{
			
			File file = new File(fileName);
			
			if(file.exists()){
				
				logger.info("++++++++++ removeassoc file is present.");
				
				boolean isDeleted = file.delete();
				
				if(isDeleted){
					logger.info("++++++++++ removeassoc file is deleted successfully.");
				}
				else{
					logger.info("++++++++++ removeassoc file is NOT deleted successfully.");
				}
			}
			else{
				logger.info("++++++++++ removeassoc file is not present.");
			}
		}
		catch(Exception ex){
			logger.error("+++++++ Exception: "+ex, ex);
		}
	}


	//generating driverDeviceAssoc everytime of login
	private void generateDriverDeviceAssoc(String otp, EntityManager em){
		DriverDeviceAssoc driDevassoc=new DriverDeviceAssoc();

		if(driver!=null && device!=null){
			driDevassoc.setDevice(device);
			driDevassoc.setDriver(driver);
			driDevassoc.setStatus(WSConstants.DRIVER_REQUESTED);
			driDevassoc.setOtp(otp);

			//em.getTransaction().begin();
			em.persist(driDevassoc);
			//em.getTransaction().commit();
			logger.info("DriverDeviceAssoc generated!!!!!!!");
		}

	}

	private void updateDriverDeviceAssoc(String otp, Driver driver){
		if(ddassoc!=null){
			//em.getTransaction().begin();
			ddassoc.setOtp(otp);
			ddassoc.setDriver(driver);
			//ddasssoc=em.merge(ddasssoc);
			//em.getTransaction().commit();
			logger.info("Otp updated for assoc-------"+ddassoc.getId()+" set otp--------"+ddassoc.getOtp()+" Driver Id---------"+ddassoc.getDriver().getDriverId());
		}
	}

	private void sendSMS(String otp, Driver drv, EntityManager em) {
		try {
			logger.info("Sending SMS to Driver "+ drv.getName());
			
			ServiceProviderApp spApp = AdCommon.getServiceProviderAppBySP(drv.getVendor().getBase().getOrg().getOrganisationId(), em);

            String spName = "ODOCON";
			
			if (spApp != null) {
				spName = spApp.getOrg().getName();
			}
			String text = "";

			text = "Your One Time Password to access your "+spName+" account is "+otp+". Thank You. bl"+"+"+"piZXrfoD";
			
			
			logger.info("Message : "+ text);
			String fromSMS = AdCommon.getFromSMSByBase(drv.getVendor().getBase(), em);
			AdCommon.insertSMSInDB(drv.getMobile(),text,fromSMS);

		} catch (Exception a) {
			logger.error("Exception " + a.getMessage());
			logger.error(GTCommon.getStackTraceAsString(a));
		}
	}

	//getting all available vehicles depends on vendor.
	@SuppressWarnings("unchecked")
	private ArrayList<String> getAllAvailableVehicles(Driver driver, EntityManager em){
		
		ArrayList<String> regNolist=new ArrayList<>();

		try{
			//	String vehiclesUsingserviceProviderId="select * from Vehicle left join DriverVehicleAssoc on Vehicle.vehicleId=DriverVehicleAssoc.vehicleId where Vehicle.busy=0 and Vehicle.serviceProviderId="+driver.getServiceProvider().getServiceProviderId()+" and Vehicle.vehicleId not in (select vehicleId from DriverVehicleAssoc where status="+WSConstants.DRIVER_ACTIVE+")";
			String vehiclesUsingVendorId="select v.* from Vehicle v where v.busy=0 and v.status= 'A' and v.vendorId="+driver.getVendor().getVendorId()+" and v.vehicleId not in (select vehicleId from DriverVehicleAssoc where status="+WSConstants.DRIVER_ACTIVE+")";
			Query query1=em.createNativeQuery(vehiclesUsingVendorId,Vehicle.class);
			logger.info("active vehicle query------------"+vehiclesUsingVendorId);

			allvehicles=(List<Vehicle>) query1.getResultList();

		}catch(NoResultException e){
			logger.error("Exception in  getAllAvailableVehicles :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in getAllAvailableVehicles :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}

		ArrayList<Vehicle> pendings=(ArrayList<Vehicle>) getPendingVehicles(driver, em);

		if(pendings != null && pendings.size() > 0){
			for(Vehicle veh : pendings){
				if(isVehPresent(veh)){
				regNolist.add(veh.getRegNo()+"*");
				}
			}

			for(Vehicle veh : allvehicles){
				if(!regNolist.contains(veh.getRegNo()+"*")){
					regNolist.add(veh.getRegNo());
				}
			}
		}
		else{
			for(Vehicle veh : allvehicles){
				regNolist.add(veh.getRegNo());
			}
		}

		logger.info("Available vehicle list----------"+regNolist);
		return regNolist;
	}
	
	//Following method is used for check whether Pending list vehicle is present in available vehicle list or not.
	//If pending vehicle is present in available vehicle list then only add into final list.
	private boolean isVehPresent(Vehicle veh){
		logger.info("------ Inside isVehPresent() Veh: "+ veh.getRegNo());
		
		boolean isPresent = false;
		
		for(Vehicle availVeh : allvehicles){
			if(availVeh.getRegNo().equalsIgnoreCase(veh.getRegNo())){
				isPresent = true;
				break;
			}
		}
		return isPresent;
	}

	public static String getOtpPassword() {
		Random r = new Random();
		String str = String.format("%04d", r.nextInt(9999));
		return str;
	}

	private String generateOTP(Driver d,String dateStr, Organisation org, EntityManager em){
		String otp = "";
		otp = getOtpPassword();
		
		long organisationId = org.getOrganisationId();
		
		OutgoingBrokerEvents obe = CommonQueries.getOBEForOrg(organisationId, WSConstants.OBE_EVENT_OTPDRIVERNOTIFICATION, em);
		
		if (obe != null) {
			
			logger.info("-------- Broker web service event " + WSConstants.OBE_EVENT_OTPDRIVERNOTIFICATION + " registered --------------");
			
			if ((brokerwsURL == null) || (brokerwsUserName == null) || (brokerwsPassword == null)) {
				
				Properties props = AdCommon.getPropFromDriverAppWS();
				brokerwsURL = props.getProperty("brokerwsURL");
				brokerwsUserName = props.getProperty("brokerwsUserName");
				brokerwsPassword = props.getProperty("brokerwsPassword");
				
			}
			
			callBWSForOTPDrivNotification(brokerwsURL, brokerwsUserName, brokerwsPassword, org, otp, d.getMobile().trim(), em);
			
		} else {
			logger.info("-------- BWS event " + WSConstants.OBE_EVENT_OTPDRIVERNOTIFICATION + " not registered, hence sending SMS --------------");
			sendSMS(otp, d,em);
		}
		
		return otp; 
	}

	private List<Vehicle> getPendingVehicles(Driver driver, EntityManager em){
		//		List<String> pendingVehicles = new ArrayList<>();
		ArrayList<Vehicle> pendings = null;
		try{
			//String vehiclesUsingserviceProviderId="select v.* from Vehicle v, PendingDriverVehicleAssoc p, Trip t where p.driverId="+driver.getDriverId()+" and v.vehicleId=p.vehicleId and t.tripId=p.tripId and date(t.reportingDate)=date(p.created)";

			String vehiclesUsingserviceProviderId = "select v.* from Vehicle v, Trip t where t.GRO IS NULL and v.vehicleId=t.vehicleId and t.status ='1' and t.driverId="+driver.getDriverId()+" and v.vendorId="+driver.getVendor().getVendorId()+" group by v.vehicleId";

			Query query1=em.createNativeQuery(vehiclesUsingserviceProviderId,Vehicle.class);
			logger.info("pending vehicle query------------"+vehiclesUsingserviceProviderId);
			pendings=(ArrayList<Vehicle>) query1.getResultList();
		}catch(NoResultException e){
			logger.error("Exception in  getPendingVehicles :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in getPendingVehicles :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}

		return pendings;
	}


	/*
	 * Following method is used for update the Trip2DeviceAssoc for those deviceId is NULL.
	 * */
	@SuppressWarnings({ "unchecked"})
	@GET
	@Path("/updatetripdeviceassoc")	
	@Consumes("application/json")
	public String updateTripDeviceAssoc(@QueryParam("tripids")String tripIds) {
		logger.info("-------- Inside updateTripDeviceAssoc ------------ tripids : "+tripIds);
		logger.info("-------- input --:"+tripIds);
		String response = "", deviceId="";
		Set<String> tIds = null;//this set hold tripIds.
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;
		try{
			et = em.getTransaction();
			
			//1) Parse the tripIds JSON.
			JSONObject jsonParse = (JSONObject)new JSONParser().parse(tripIds);

			//JSONObject jsonDevice = (JSONObject)jsonParse.get("imei");

			deviceId = String.valueOf(jsonParse.get("imei"));

			//Get Device from deviceId
			Device device=CommonQueries.getDeviceUsingImei(deviceId,em);

			JSONArray jsonTripIds = (JSONArray)jsonParse.get("tripids");

			if((jsonTripIds != null) && (jsonTripIds.size() > 0)){
				tIds = new TreeSet<String>();
				for(int i = 0; i < jsonTripIds.size(); i++){
					JSONObject obj = (JSONObject) jsonTripIds.get(i);
					String id = obj.keySet().toString();
					tIds.add(id.substring(1,id.length()-1));
				}
			}

			if( (tIds != null) && (!tIds.isEmpty()) ){

				//Iterate tIds Set.
				for(String tripId : tIds){

					if((tripId == null) || tripId.trim().isEmpty()){
						continue;
					}

					logger.info("--------TripId: "+tripId);

					// Check deviceId is NULL or not for tripId in Trip2DeviceAssoc.
					et.begin();
					String queryString="select * FROM Trip2DeviceAssoc where tripId="+tripId;
					logger.info("---------assoc query --------"+queryString);
					Query query=em.createNativeQuery(queryString,Trip2DeviceAssoc.class);
					try {
						Trip2DeviceAssoc tripDeviceAssoc = (Trip2DeviceAssoc) query.getSingleResult();

						if((tripDeviceAssoc != null) && (tripDeviceAssoc.getDevice() == null)){

							tripDeviceAssoc.setDevice(device);

							em.merge(tripDeviceAssoc);
						}
					}catch(NonUniqueResultException e){
						logger.error("No single result for Trip2DeviceAssoc:"+e.getMessage(), e);
					}
					catch(Exception e1){
						logger.error("Normal Exception:"+e1.getMessage(), e1);
					}
					et.commit();
				}
			}
			response = "success";
		}
		catch(Exception e){
			logger.error("------------Exception in updateTripDeviceAssoc :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			response = "error";
			if(et.isActive()){
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		}
		finally {
			if (em.isOpen()) {
				em.close();
			}
		}
		return response;
	}

	//Following method is used for check the version of app.
	private JSONObject checkAppVersion(String version){
		logger.info("++++++++++++ Inside checkAppVersion");
		JSONObject versionJSON = new JSONObject();
		try{

			if((version != null) && (! version.isEmpty())){
				boolean isUpgradable = false;

				if (!currentVersion.equals(version)) {
					isUpgradable = true;
				}

				versionJSON.put("isUpgradable", isUpgradable);
				if (isComp) {

					SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd");
					Date today = new Date();
					Date expiryDate = today;

					try {
						expiryDate = df.parse(oldVersionExpiryDate);
					} catch (java.text.ParseException e) {
						logger.info("Exception !", e);
					}
                    logger.info("+++++++ Today Date: "+today);
                    logger.info("+++++++ Expiry Date: "+expiryDate);
					if (today.compareTo(expiryDate) > 0) {
						logger.info("+++++++ Inside Date compare if");
						isComp = false;
					}
				}
				versionJSON.put("isCompatible", isComp);

				if (isUpgradable) {
 					versionJSON.put("appUpgradeLink", appUpgradeLink);
					versionJSON.put("appUpgradeMessage", appUpgradeMessage);
				}
			}
			else{
				versionJSON.put("isUpgradable", true);
				versionJSON.put("isCompatible", isComp);
				versionJSON.put("appUpgradeLink", appUpgradeLink);
				versionJSON.put("appUpgradeMessage", appUpgradeMessage);
			}
		}
		catch(Exception ex){
			logger.error("++++++++ Excpetion: "+ex.getMessage(), ex);
		}
        logger.info("+++++++++ Return: "+versionJSON.toString());
		return versionJSON;
	}
	
	
private boolean callBWSForOTPDrivNotification (String brokerwsURL, String bwsUserName, String bwsPasswd, Organisation org, String OTP, String driverMobNo, EntityManager em) {
		
		boolean success = false;
		
		try {
			
			logger.info("----- Calling broker web service --------");
			
			if ((brokerwsURL != null) && (!brokerwsURL.isEmpty())) {
				
				String wsURL = null;
				Gson gson = new Gson();
				
				String methodName = "sendDriverOTPNotification";
				String queryParam1 = "inputData";
				BwsOTPDriverNotificationBean bwsBean = new BwsOTPDriverNotificationBean(OTP, driverMobNo);
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
				
				em.persist(obt);
				
				
			} else {
				logger.info("---------- brokerwsURL property not found in props file ------------");
			}
			
		} catch (Exception e) {
			logger.info("Exception !", e);
		}
		
		
		return success;
		
	}
	
	//getting deviceId using driverId
	private long getDeviceIdUsingDriverId(long driverId, EntityManager em){
		long deviceId = 0;
		try {
			String driverDeviceAssocUsingDriverId="select * from DriverDeviceAssoc dd where dd.driverId="+driverId;
			Query query=em.createNativeQuery(driverDeviceAssocUsingDriverId,DriverDeviceAssoc.class);
			ddassocList=query.getResultList();
			if(ddassocList.size()!=0)
				deviceId = ddassocList.get(0).getDevice().getDeviceId();
			else
				return -1;
			logger.info("Device Id :"+ deviceId);

		}catch(NoResultException e){
			logger.info("Device Id not found for mobile No.: "+driverId);
		}
		catch (Exception e) {
			logger.error("Exception in get DeviceId Using DriverId :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return deviceId;
	}
	
	// getting Imei using deviceId
	private String getImeiUsingDeviceId(long deviceId, EntityManager em) {
		String imeiNo="";
		try {
			String getImeiUsingDeviceId = "select * from Device d where d.deviceId="+deviceId;
			Query query = em.createNativeQuery(getImeiUsingDeviceId,Device.class);
			device = (Device)query.getSingleResult();
			imeiNo = device.getSerialNumber();
			logger.info("IMEI :" + imeiNo);

		} catch (NoResultException e) {
			logger.info("IMEI not found for Device Id : " + deviceId);
		} catch (Exception e) {
			logger.error("Exception in get IMEI Using DeviceId :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		return imeiNo;
	}
	
	
}
