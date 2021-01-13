package com.adeona.ws;

import java.util.Date;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jboss.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.adeona.orm.Device;
import com.adeona.orm.Trip;
import com.adeona.orm.VehicleDeviceLocation;
import com.gt.util.GTCommon;

@Path("/Alert")
public class Alert {
	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("odocon");

	//EntityManager em;
	Logger logger = Logger.getLogger(Alert.class);
	JSONObject jsonObj;
	private static String directUrl = null;
	
	public Alert() {
		logger.info("-----------------ALERT----------------------------");
		//em = emf.createEntityManager();
		 Properties props = AdCommon.getPropFromDriverAppWS();
		 if (directUrl == null ){
			 if (props.containsKey("directUrl")){
				 directUrl = props.getProperty("directUrl");
				 directUrl = directUrl.trim();
			 } 
		 }
		 
		 
	}
	

	
	@POST
	@Path("/SendAlert")
	public String sendAlert(String jsonString){
		String ret=null;
		Device device;

		logger.info("sendAlert Json------------------"+jsonString);
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;
		try {
			et = em.getTransaction();
			et.begin();
			
			jsonObj = (JSONObject)new JSONParser().parse(jsonString.toString());

			com.adeona.orm.Alert alert=new com.adeona.orm.Alert();
			String alerttype=(String) jsonObj.get("alert");
			long locTime=(long) jsonObj.get("loctime");
			Date date=new Date(locTime);
			String imei=(String) jsonObj.get("imei");
			device=getDeviceUsingImei(imei, em);


			if(alerttype.equals("GPS enabled")){
				//em.getTransaction().begin();
				alert.setAlertType("GPS");
				alert.setSeverity("low");
				alert.setMessage("GPS Enabled");
				alert.setDate(date);
				alert.setDevice(device);
				alert.setBase(device.getOrgBase());
				em.persist(alert);
				//em.getTransaction().commit();
				ret = "Success";
			}
			else if(alerttype.equals("GPS disabled")){
				//em.getTransaction().begin();
				alert.setAlertType("GPS");
				alert.setSeverity("critical");
				alert.setMessage("GPS Disabled");
				alert.setDate(date);
				alert.setDevice(device);
				alert.setBase(device.getOrgBase());
				em.persist(alert);
				//em.getTransaction().commit();
				ret="Success";
			}
			else if(alerttype.contains("Power")){
				//em.getTransaction().begin();
				alert.setAlertType("Power");
				alert.setSeverity("critical");
				alert.setMessage("Power button long pressed");
				alert.setDate(date);
				alert.setDevice(device);
				alert.setBase(device.getOrgBase());
				em.persist(alert);
				//em.getTransaction().commit();
				ret="Success";
			}
			else{
				ret="Error";
			}
			et.commit();
			if (directUrl != null){
				String  url = AdCommon.createDirectUrl(directUrl);
				AdCommon.callDirectUrl(url);
			}
			
			
		} catch (Exception e) {
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
		return ret;
	}
	
	
	//getting device using imei
		private Device getDeviceUsingImei(String imei, EntityManager em){
			Device device = null;
		
			try{		
				String querryToFindDevice="FROM Device where serialNumber="+imei;
				Query queryTogetEmployeeId = em.createQuery(querryToFindDevice);	
				logger.info("query-------------"+querryToFindDevice);
				device  = (Device) queryTogetEmployeeId.getSingleResult();		
			} catch(NoResultException e){
				logger.error("Exception for no trip corresponding to trip id in request:"+e.getMessage() );
//				logger.error(GTCommon.getStackTraceAsString(e));
				device=null;
			}
			catch(Exception e1){
				logger.error("Normal Exception:"+e1);
				logger.error(GTCommon.getStackTraceAsString(e1));
				device=null;
			}
			return device;
		}
		
}
