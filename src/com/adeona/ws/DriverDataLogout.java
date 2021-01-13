package com.adeona.ws;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jboss.logging.Logger;
import org.json.simple.JSONObject;

import com.adeona.orm.DriverDeviceAssoc;
import com.adeona.orm.DriverVehicleAssoc;
import com.adeona.orm.Trip;
import com.gt.util.GTCommon;

@Path("/DriverDataLogout")
public class DriverDataLogout {

	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("odocon");
	//EntityManager em;
	Logger logger = Logger.getLogger(DriverRelationshipGeneration.class);
	String urlvalue = null;
	List<DriverDeviceAssoc> ddassoc=null;
	List<DriverVehicleAssoc> dvassoc=null;
	static final String DEVICE_DATA_DIR = "/usr/gt/android/devices/";
	static final String PENDING_DATA_DIR = "/usr/gt/android/devices/pendingTrips/";
	public DriverDataLogout() {
		logger.info("**************************LOGOUT******************************");
		Properties prop = AdCommon.getPropFromDriverAppWS();
		//em = emf.createEntityManager();
		if (prop != null) {
			urlvalue = prop.getProperty("server");
		}
	}


	private List<DriverDeviceAssoc> getDriverDeviceAssocUsingimei(String imei, EntityManager em){

		try {
			String driverObj="select * from DriverDeviceAssoc d, Device device where d.deviceId=device.deviceId and device.serialNumber="+imei+" and d.status="+WSConstants.DRIVERVEHICLEASSOC_ACTIVE;			
			Query query=em.createNativeQuery(driverObj,DriverDeviceAssoc.class);
			logger.info("Query----------"+driverObj);
			ddassoc=(List<DriverDeviceAssoc>) query.getResultList();
			//logger.info("Driver :"+ddassoc.getDriver().getDriverId());

		}catch(NoResultException e){
			logger.error("Exception in  driverAssocInactive :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in driverAssocInactive :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return ddassoc;
	}


	private List<DriverVehicleAssoc> getDriverVehicleAssocUsingdriverId(long driverId, EntityManager em){

		try {
			String driverObj="From DriverVehicleAssoc where driverId="+driverId +" and status="+WSConstants.DRIVERVEHICLEASSOC_ACTIVE;			
			Query query=em.createQuery(driverObj);
			dvassoc=(List<DriverVehicleAssoc>) query.getResultList();
			//logger.info("Vehicle :"+dvassoc.getVehicle().getVehicleId());

		}catch(NoResultException e){
			logger.error("Exception in  driverAssocInactive :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in driverAssocInactive :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return dvassoc;
	}


	//creating new DriverDeviceAssoc
	@GET
	@Path("/Driverlogout")	
	@Consumes({"multipart/related,text/plain"})
	public String driverlogout(@QueryParam("driverId")long drvrId){

		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;
        long driverId = 0;
        String response = "";
		JSONObject responsse=new JSONObject();
		String ret =null;
        
        List<String> tripIds = null;
        
		try {
			logger.info("--------------------DRIVER LOGOUT----------------");
			logger.info("--------------------DRIVER LOGOUT----------------driverId : "+drvrId);
			et = em.getTransaction();
			et.begin();
			
			long deviceid = CommonQueries.getDeviceIdUsingDriverId(drvrId, em);
			if (deviceid == -1) {
				responsse.put("message", "Error: Device Id not available against the Driver Id");
				responsse.put("statusCode", 500);
				ret = responsse.toString();
				return ret;
			}
			String imei = CommonQueries.getImeiUsingDeviceId(deviceid, em);
			
			ddassoc=getDriverDeviceAssocUsingimei(imei, em);
			
			if(ddassoc != null && ddassoc.size() > 0){
				
				List<Trip> activeTripList = AdCommon.getDriverActiverTrip(ddassoc.get(0).getDriver().getDriverId(), em); // all driver device assoc will have same Driver id
				if ((activeTripList != null)  ){
					Iterator<Trip> itr = activeTripList.iterator();
					while (itr.hasNext()) {
						Trip trip = (Trip) itr.next();
						// added this logger for logging purpose.....
						
						if (!trip.isOffline()) {
							logger.info("-------Driver can not logout active trip is "+trip.getTripId());
							//return "failed:Driver already in trip";
							responsse.put("message", "failed:Driver already in trip");
							responsse.put("statusCode", 500);
							ret = responsse.toString();
							return ret;
						}
						
					}
					
					
				} else {
					logger.info("-------NO active trip availble Driver can logout ................");
				}
				
				for(DriverDeviceAssoc dda : ddassoc){
					
					driverId = dda.getDriver().getDriverId();
					logger.info("-------Driver Id: "+driverId);
					
					dda.setStatus(WSConstants.DRIVERVEHICLEASSOC_INACTIVE);
				}
			}
			
			dvassoc=getDriverVehicleAssocUsingdriverId(driverId, em);
			
			if(dvassoc != null && dvassoc.size() > 0){
				
				for(DriverVehicleAssoc dva : dvassoc){
					logger.info("-------Vehicle Reg No.: "+dva.getVehicle().getRegNo());

					dva.setStatus(WSConstants.DRIVERVEHICLEASSOC_INACTIVE);
				}
			}
			//Following code is for getting remaining trip files and delete them.
			//Step 1: Get the all files name from directory.
			File folder = new File(DEVICE_DATA_DIR + imei);
			File[] listOfFiles = folder.listFiles(new FilenameFilter() {
				
				@Override
				public boolean accept(File dir, String name) {
					// TODO Auto-generated method stub
					return name.startsWith("task");
				}
			});
			
			
			try
			{
			logger.info("---------------No. of files found: "+listOfFiles.length);
			
			if(listOfFiles.length > 0){
				
				tripIds = new ArrayList<String>();
				
				for (int i = 0; i < listOfFiles.length; i++) {
					if (listOfFiles[i].isFile()) {
						
						if(!(listOfFiles[i].getName().endsWith(".done"))){
							
							logger.info("-------Ready for move into Pending Trips File Name: "+listOfFiles[i].getName());
							
							// sample file name task_TripSheet_21_4_4_kirti-Naik_10-02-2017-13:03
							String[] splitTripName = listOfFiles[i].getName().split("_");
							
							logger.info("---------Trip Id is : "+splitTripName[2]);

							if (splitTripName.length > 3) {
								moveTripSheetToPendingTrip(listOfFiles[i].getName(), imei);
							
								 boolean b = listOfFiles[i].delete();
								logger.info("------- trip sheet deleted ......"+b);

								tripIds.add(splitTripName[2]);
							
								logger.info("------- Moved file successfully.");
							} else {
								logger.info("ignoring file " + listOfFiles[i].getName());
							}
						}
						
					}
				}
				updateTripDeviceAssoc(tripIds, em);
			}
			}
			catch (NullPointerException e) {
				logger.info("---------------No. of files found: 0");
			}
			
			et.commit();
			
		} catch (Exception e) {
			logger.error("Exception in  Driver Logout :"+ e.getMessage());
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
		
		responsse.put("message", "Success");
		responsse.put("statusCode", 200);
		ret = responsse.toString();
		return ret;
		
		//return "Success";

	}
	
	//Following method is used for set the deviceId is NULL for respected tripId.
	public void updateTripDeviceAssoc(List<String> tripIds, EntityManager em){
		logger.info("-------- Inside updateTripDeviceAssoc");
		EntityTransaction et = null;

		String queryString = "update Trip2DeviceAssoc set deviceId = NULL where tripId = ";
		
		try{
			//et = em.getTransaction();

			if(!(tripIds.isEmpty())){
				
				
				for(String tripId : tripIds){
					
					logger.info("----------Trip Id: "+tripId);
					if ((tripId == null) || tripId.trim().isEmpty()) {
						continue;
					}
					
					//et.begin();
					
					try {
						Query query =  em.createQuery(queryString+tripId);
					
						int i = query.executeUpdate();
					} catch (Exception e) {
						logger.error(e);
					}
					
					//et.commit();
				}
			}
		}
		catch(Exception e){
			logger.error("Exception in  updateTripDeviceAssoc for NULL value :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			if(et.isActive()){
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		}
	}
	
	//Following method is used for move the trip sheet to the pending Trip folder.
	private void moveTripSheetToPendingTrip(String fileName, String imei){
		logger.info("------------Inside moveTripSheetToPendingTrip");
		try{
			
			File oldFile =new File(DEVICE_DATA_DIR + imei+"/"+fileName);
			File moveFile =new File(PENDING_DATA_DIR+fileName);
			
			InputStream inStream = new FileInputStream(oldFile);
			OutputStream outStream = new FileOutputStream(moveFile);

			byte[] buffer = new byte[1024];

			int length;
			//copy the file content in bytes
			while ((length = inStream.read(buffer)) > 0){

				outStream.write(buffer, 0, length);

			}

			inStream.close();
			outStream.close();
			
			/*File reconfFile=new File(DEVICE_DATA_DIR + imei+"/reconf");
			reconfFile.createNewFile();*/
			
		}
		catch(Exception e){
			logger.error("Exception in  moveTripSheetToPendingTrip:"+ e.getMessage());
		}
		
	}
	
	
}
