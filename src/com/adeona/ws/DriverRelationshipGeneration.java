
package com.adeona.ws;

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

import com.adeona.orm.Device;
import com.adeona.orm.Driver;
import com.adeona.orm.DriverDeviceAssoc;
import com.adeona.orm.DriverVehicleAssoc;
import com.adeona.orm.Vehicle;
import com.gt.util.GTCommon;

@Path("/AssocGeneration")
public class DriverRelationshipGeneration {
	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("odocon");
	//EntityManager em;
	Logger logger = Logger.getLogger(DriverRelationshipGeneration.class);
	String urlvalue = null;
	Device device;
	Driver driver;
	Vehicle vehicle;

	
	public DriverRelationshipGeneration() {
		logger.info("*************************ASSOC CREATION*******************************");
		Properties prop = AdCommon.getPropFromDriverAppWS();
		//em = emf.createEntityManager();
		if (prop != null) {
			urlvalue = prop.getProperty("server");
		}
	}
	
	
	
	private Vehicle getVehicleUsingRegNo(String regNo, EntityManager em){
		Vehicle veh=null;
		try {
			String vehicleUsingRegNo="From Vehicle where regNo='"+regNo+"'";			
			Query query=em.createQuery(vehicleUsingRegNo);
			veh=(Vehicle) query.getSingleResult();
			logger.info("Vehicle :"+veh.getVehicleId());
			
		}catch(NoResultException e){
			logger.error("Exception in  generateDriverDeviceRelation :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in generateDriverDeviceRelation :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return veh;
	}
	
	
	
	private Driver getDriverUsingMobileNumber(String mobileNumber,EntityManager em){
		try {
			String driverUsingMobile="From Driver where mobile="+mobileNumber+" and status='A'";			
			Query query=em.createQuery(driverUsingMobile);
			driver=(Driver) query.getSingleResult();
			logger.info("Driver :"+driver.getDriverId());
			
		}catch(NoResultException e){
			logger.error("Exception in  generateDriverDeviceRelation :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		catch (Exception e) {
			logger.error("Exception in generateDriverDeviceRelation :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));	
		}
		return driver;
		
	}
	
	
	//creating new DriverVehicleAssoc
	@GET
	@Path("/DriverLogin")	
	@Consumes({"multipart/related,text/plain"})
	public String driverLogin(@QueryParam("mobileNumber")String mobileNumber,@QueryParam("regNo")String regNo,@QueryParam("imei")String imei) {
	    String ret=null;
	    EntityManager em = emf.createEntityManager();
	    EntityTransaction et = em.getTransaction();
	    
	    try {
	    	et.begin();
	    	logger.info("-----------------------DRIVER LOGIN------------------------------mobileNumber : "+mobileNumber +" regNo : "+regNo +" imei : "+imei);
	    	logger.info("\n\n\n \t\t\t vehicle from request------------------"+regNo);
	    	if(regNo.contains("*")){
	    		regNo=regNo.substring(0,regNo.length()-1);
	    		logger.info("after remove * from reg no---------------"+regNo);
	    	}
	    	device=CommonQueries.getDeviceUsingImei(imei,em);
	    	vehicle=getVehicleUsingRegNo(regNo, em);
	    	driver=getDriverUsingMobileNumber(mobileNumber, em);

	    	if ((driver != null) && (vehicle != null)) {
		    	DriverVehicleAssoc driVehassoc=new DriverVehicleAssoc();
	
		    	driVehassoc.setDriver(driver);
		    	driVehassoc.setVehicle(vehicle);
		    	driVehassoc.setStatus(WSConstants.DRIVERVEHICLEASSOC_ACTIVE);
		    	em.persist(driVehassoc);
	
		    	//updating status to active of DriverDeviceAssoc
		    	DriverDeviceAssoc driDevassoc = null;
		    	try{
		    		String driDevassocUsingdeviceIdanddriverId="From DriverDeviceAssoc where driverId= "+driver.getDriverId() +" and deviceId= "+device.getDeviceId()+" and status="+WSConstants.DRIVER_REQUESTED;
		    		logger.info("\n\n\n \t\t\t query-----------"+driDevassocUsingdeviceIdanddriverId);
		    		Query query=em.createQuery(driDevassocUsingdeviceIdanddriverId);
		    		driDevassoc=(DriverDeviceAssoc) query.getSingleResult();
		    	}catch(NoResultException e){
		    		logger.info("Driver Device Assoc not found.");
		    		//logger.error(GTCommon.getStackTraceAsString(e));
		    		ret="Error";
		    	}
		    	catch(Exception e1){
		    		logger.error("Normal Exception:"+e1);
		    		logger.error(GTCommon.getStackTraceAsString(e1));
		    		ret="Error";
		    	}
		    	//et.begin();
		    	driDevassoc.setStatus(WSConstants.DRIVER_ACTIVE);
		    	et.commit();
				//Create JSON object with result, driverId, vehicleId.
				JSONObject json = new JSONObject();
				
				json.put("response", "success");
				json.put("driverId", driver.getDriverId());
				json.put("vehicleId", vehicle.getVehicleId());
				
				ret = json.toString();

		    } 
	    	else {
	    		ret = "Error";
	    	}
	    }catch (Exception e) {
				logger.error("Exception in  Driver Login :"+ e.getMessage());
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
		logger.info("-------- Response to Driver App ." + ret);
		return ret;
	}
}
