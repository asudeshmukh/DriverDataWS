package com.adeona.ws;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import org.apache.commons.lang3.time.DateUtils;

import javax.imageio.ImageIO;
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

import org.apache.poi.ss.usermodel.DateUtil;
import org.jboss.logging.Logger;
import org.jfree.date.DateUtilities;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.helper.DataUtil;

import com.adeona.orm.Alert;
import com.adeona.orm.Battery;
import com.adeona.orm.BillingRule;
import com.adeona.orm.BookingRequest;
import com.adeona.orm.Device;
import com.adeona.orm.Driver;
import com.adeona.orm.DriverDeviceAssoc;
import com.adeona.orm.DriverVehicleAssoc;
import com.adeona.orm.Employee;
import com.adeona.orm.EmployeeCheckInOut;
import com.adeona.orm.InvoiceHeader;
import com.adeona.orm.OrgBase;
import com.adeona.orm.OrgCorporateAssoc;
import com.adeona.orm.OrgRateTypeInvoiceAssoc;
import com.adeona.orm.OutgoingBrokerEvents;
import com.adeona.orm.ServiceProviderApp;
import com.adeona.orm.Tariff;
import com.adeona.orm.Trip;
import com.adeona.orm.Trip2DeviceAssoc;
import com.adeona.orm.UserFeedback;
import com.adeona.orm.Vehicle;
import com.adeona.orm.VehicleDeviceLocationToGT;
import com.adeona.orm.Vendor;
import com.gt.util.GTCommon;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


import Decoder.BASE64Decoder;

@Path("/TripDetails")
public class TripDetails {
	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("odocon");

	// EntityManager em;
	Logger logger = Logger.getLogger(DriverDataLogin.class);
	static String urlvalue = null;
	JSONObject jsonObj = null;
	Trip trip = null;
	EmployeeCheckInOut ecio = null;
	Device device = null;
	Driver driver = null;
	Vehicle vehicle = null;
	static final String DEVICE_DATA_DIR = "/usr/gt/android/devices/";
	double totalAmount = 0;
	static Properties props;
	Battery batteryy;
	Alert alertobj;
	InvoiceHeader ih;
	BookingRequest br;
	BillingRule billingruleForInvoice;
	private static String directUrl = null;
	private static String brokerwsURL = null;
	private static String brokerwsUserName = null;
	private static String brokerwsPassword = null;
	int durationHours;
	int durationMinutes;
	static final long ONE_MINUTE_IN_MILLIS=60000;//millisecs
	
	private static final String API_KEY = "AIzaSyA1Mf8fmcI-E_WR2231oEJvXtnYfmSbjXU";
	OkHttpClient client = new OkHttpClient();

	public TripDetails() {
		logger.info("************************DATA UPDATION********************************");
		// em = emf.createEntityManager();

	}

	static {
		props = AdCommon.getPropFromDriverAppWS();
		if (props != null) {
			urlvalue = props.getProperty("server");
			if (directUrl == null) {
				if (props.containsKey("directUrl")) {
					directUrl = props.getProperty("directUrl");
					directUrl = directUrl.trim();
				}
			}
		}
	}

	private Vehicle getVehicleUsingimei(String imei, EntityManager em) {

		try {
			String querryToFindVehicle = "select * from Vehicle veh, DriverVehicleAssoc v, DriverDeviceAssoc d, Device device where veh.vehicleId=v.vehicleId and v.status="
					+ WSConstants.DRIVERVEHICLEASSOC_ACTIVE + " and d.status=" + WSConstants.DRIVERVEHICLEASSOC_ACTIVE
					+ " and v.driverId=d.driverId and device.serialNumber=" + imei;
			Query queryTogetEmployeeId = em.createNativeQuery(querryToFindVehicle, Vehicle.class);
			logger.info("Query-----------" + querryToFindVehicle);
			vehicle = (Vehicle) queryTogetEmployeeId.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no trip corresponding to trip id in request:" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			vehicle = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			vehicle = null;
		}
		return vehicle;
	}

	private Vehicle getVehicleUsingVehicleId(long vehicleId, EntityManager em) {

		try {
			String querryToFindVehicle = "select * from Vehicle where vehicleId=" + vehicleId;
			Query queryTogetEmployeeId = em.createNativeQuery(querryToFindVehicle, Vehicle.class);
			logger.info("Query-----------" + querryToFindVehicle);
			vehicle = (Vehicle) queryTogetEmployeeId.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no vehicle corresponding to vehicle id in request:" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			vehicle = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			vehicle = null;
		}
		return vehicle;
	}

	// getting Battery using deviceId
	private Battery getBatteryUsingDeviceId(long deviceId, EntityManager em) {
		Battery batteryy = null;
		try {
			String batteryObject = "From Battery where deviceId=" + deviceId;
			logger.info("query----------" + batteryObject);
			Query queryTogetBattery = em.createQuery(batteryObject);
			batteryy = (Battery) queryTogetBattery.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no Battery corresponding to battery id in request:" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			// logger.error(GTCommon.getStackTraceAsString(e1));
		}

		return batteryy;
	}

	private Alert getAlertUsingDeviceId(long deviceId, EntityManager em) {
		try {
			String alertObject = "From Alert where deviceId=" + deviceId;
			logger.info("query----------" + alertObject);
			Query queryTogetAlert = em.createQuery(alertObject);
			alertobj = (Alert) queryTogetAlert.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no Alert corresponding to alert id in request:" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			alertobj = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			// logger.error(GTCommon.getStackTraceAsString(e1));
			alertobj = null;
		}

		return alertobj;
	}

	private BookingRequest getBookingRequestUsingEmployeeId(long employeeId, EntityManager em) {
		try {
			String brObject = "From BookingRequest where employeeId=" + employeeId;
			logger.info("query----------" + brObject);
			Query queryTogetBookingReq = em.createQuery(brObject);
			br = (BookingRequest) queryTogetBookingReq.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no BookingRequest corresponding to employee id in request:" + e.getMessage());
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
		}

		return br;
	}

	private double getDistanceUsingLatLong(String source, String destination) {
		double distance = 0.0;
		Response response = null;
		
		try {
			String url = "https://maps.googleapis.com/maps/api/distancematrix/json?origins=" + source + "&destinations="
					+ destination + "&key=" + API_KEY;
			logger.info("getDistanceLatLong URL-------------: " + url);
			Request request = new Request.Builder().url(url).build();
			response = client.newCall(request).execute();
			String result = response.body().string();
			
			try {
				jsonObj = (JSONObject) new JSONParser().parse(result.toString());
			} catch (ParseException e) {
				logger.error("------Excpetion:" + e.getMessage(), e);
			}

			JSONArray dist = (JSONArray) jsonObj.get("rows");
			JSONObject obj2 = (JSONObject) dist.get(0);
			JSONArray disting = (JSONArray) obj2.get("elements");
			JSONObject obj3 = (JSONObject) disting.get(0);
			JSONObject obj4 = (JSONObject) obj3.get("distance");
			JSONObject obj5 = (JSONObject) obj3.get("duration");
			String durationString = (String) obj5.get("text");			
			durationString = (((durationString.replace(" hours ", ",")).replace(" hour ", ",")).replace(" mins", "")).replace(" min", "");
			String ar[] = durationString.split(",");
			if(ar.length == 2){
				durationHours = Integer.parseInt(ar[0]);
				durationMinutes = Integer.parseInt(ar[1]);
			}
			else if (ar.length == 1){
				durationHours = 0;
				durationMinutes = Integer.parseInt(ar[0]);
			}
			
			logger.info("Time duration: "+ durationHours + " hours " + durationMinutes +" minutes");
			String distString = (String) obj4.get("text");
			distance = Double.parseDouble((distString.replace(" km", "")).replace(",", ""));
			
		} catch (NoResultException e) {
			logger.error("Exception in calculating distance using Source and destination lat,long " + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
		}
		finally {
			response.close();
		}
		return distance;
	}

	@GET
	@Path("/TripStart")
	@Consumes({ "multipart/related,text/plain" })
	public String tripStart(@QueryParam("tripId") long tripId, @QueryParam("time") long time,
			@QueryParam("driverId") long driverId, @QueryParam("lat") double lat, @QueryParam("lng") double lng) {
		String ret = null;
		JSONObject responsse = new JSONObject();
		/*
		 * if(!em.isOpen()){ em = emf.createEntityManager(); }
		 */
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;

		try {
			logger.info("---------------------------TRIP START---------------------");
			logger.info("----Input: " + " tripId: " + tripId + " time: " + time + " driverId: " + driverId + " lat: "
					+ lat + " lng: " + lng);
			et = em.getTransaction();
			et.begin();

			int trpCurrentStatus = CommonQueries.getTripCurrentStatus(tripId, em);
			List<Trip> tripList = CommonQueries.getTripUsingDriverId(driverId, em);
			if (tripList != null) {
				boolean isTripAssignedToDriver = false;
				for (Trip trp : tripList) {
					if (trp.getTripId() == tripId) {
						isTripAssignedToDriver = true;
						break;
					}
				}

				if (isTripAssignedToDriver) {
					long deviceid = CommonQueries.getDeviceIdUsingDriverId(driverId, em);
					if (deviceid == -1) {
						responsse.put("message", "Error: Device Id not available against the Driver Id");
						responsse.put("statusCode", 500);
						responsse.put("tripcurrentstatus", trpCurrentStatus);
						ret = responsse.toString();
						return ret;
					}
					String imei = CommonQueries.getImeiUsingDeviceId(deviceid, em);

					logger.info("----IMEI: " + imei);

					trip = CommonQueries.getTripUsingTripId(tripId, em);
					if (trip != null) {
						// if(checkDriverVehicleAssoc(trip, imei, em)){
						if (trip.getStatus() == 1) {

							Date pickTime = trip.getBookingReq().getBookingTime();

							if (pickTime != null) {

								Date today = new Date();
								boolean isFuture = AdCommon.isFutureTrip(pickTime, today);

								if (isFuture) {

									logger.info("trip not of today ...can not be exceuted .... reporting time "
											+ pickTime.toString() + " today " + today.toString());
									responsse.put("message", "Error: Future trip cannot start now!");
									responsse.put("statusCode", 500);
									responsse.put("tripcurrentstatus", trpCurrentStatus);
									ret = responsse.toString();
									return ret;
									// return "Future trip cannot start now";
								}
							}
							ecio = CommonQueries.getEciousingTrip(tripId, em);

							trip.setGRO(new Date(time));
							float groLat = new Double(lat).floatValue();
							float groLng = new Double(lng).floatValue();
							trip.setGroLatitude(groLat);
							trip.setGroLongitude(groLng);
							trip.setGroKm(0.0);
							trip.setLastTimeStamp(System.currentTimeMillis());
							trip.setDutyStatus("Ongoing");
							Trip addedTrip = em.merge(trip);
							// Update Trip2DeviceAssoc.
							String queryString = "From Trip2DeviceAssoc where tripId=" + tripId;
							logger.info("query Trip2DeviceAssoc: ----------" + queryString);
							Query query = em.createQuery(queryString);
							Trip2DeviceAssoc objTtoDA = (Trip2DeviceAssoc) query.getSingleResult();
							if (objTtoDA.getDevice() == null) {
								Device objDevice = CommonQueries.getDeviceUsingImei(imei, em);
								objTtoDA.setDevice(objDevice);
								em.merge(objTtoDA);
							}

							et.commit();
							// Call the AdCommon sendOTP.
							if (ecio.getOtpPassword() != null) {
								OutgoingBrokerEvents obe = CommonQueries.getOBEForOrg(
										addedTrip.getBase().getOrg().getOrganisationId(),
										WSConstants.OBE_EVENT_OTPCUSTNOTIFICATION, em);

								if (obe != null) {

									if ((brokerwsURL == null) || (brokerwsUserName == null)
											|| (brokerwsPassword == null)) {

										Properties props = AdCommon.getPropFromDriverAppWS();
										brokerwsURL = props.getProperty("brokerwsURL");
										brokerwsUserName = props.getProperty("brokerwsUserName");
										brokerwsPassword = props.getProperty("brokerwsPassword");

									}

									if (CommonQueries.isBWSCallValidIfLTBooking(addedTrip, em)) {
										AdCommon.callBrokerWebService(addedTrip,
												WSConstants.OBE_EVENT_OTPCUSTNOTIFICATION, brokerwsURL,
												brokerwsUserName, brokerwsPassword, em);
									}

								} else {
									logger.info(
											"------- Broker web service not registered, hence sending otp message ---------");
									AdCommon.sendOTP(addedTrip, ecio.getOtpPassword(), em);
								}
							}
							responsse.put("message", "Success");
							responsse.put("statusCode", 200);
							responsse.put("tripcurrentstatus", trpCurrentStatus);
							ret = responsse.toString();
							callDirectUrl();
						} else {
							logger.info("---------Trip is Inactive");
							responsse.put("message", "inactive");
							responsse.put("statusCode", 500);
							responsse.put("tripcurrentstatus", 6);
							ret = responsse.toString();

						}
						// }
						// else{
						// logger.info("--------- Driver already release from
						// portal or Driver and/or device changed");
						// responsse.put("message", "logout!");
						// responsse.put("statusCode", 200);
						// ret = responsse.toString();
						// }
					} else {
						responsse.put("message", "Error");
						responsse.put("statusCode", 500);
						responsse.put("tripcurrentstatus", trpCurrentStatus);
						ret = responsse.toString();
					}
				} else {
					responsse.put("message", "This trip is not assigned to driver");
					responsse.put("statusCode", 500);
					responsse.put("tripcurrentstatus", trpCurrentStatus);
					ret = responsse.toString();
				}
			} else {
				logger.info("TripId not found for DriverId.: " + driverId);
				responsse.put("message", "This trip is not assigned to driver");
				responsse.put("statusCode", 500);
				responsse.put("tripcurrentstatus", trpCurrentStatus);
				ret = responsse.toString();
			}

			if (responsse.containsValue(200) && responsse.containsValue("Success")) {
				et.begin();
				trip.setTripCurrentStatus(1);
				em.merge(trip);
				et.commit();
			}

		} catch (Exception e) {
			responsse.put("message", "Error: Please try again");
			responsse.put("statusCode", 500);
			responsse.put("tripcurrentstatus", 0);
			ret = responsse.toString();
			// ret = "Error, Please try again";
			logger.error("Exception in  Trip Start :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			if (et.isActive()) {
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		} finally {
			if (em.isOpen()) {
				em.close();
			}
		}
		logger.info("---------Trip Start Response: " + ret);
		return ret;
	}

	@GET
	@Path("/GuestOpen")
	@Consumes({ "multipart/related,text/plain" })
	public String guestOpen(@QueryParam("driverId") long driverId, @QueryParam("tripId") long tripId,
			@QueryParam("time") long time, @QueryParam("lat") double lat, @QueryParam("lng") double lng) {
		String ret = null;
		String otp;
		JSONObject responsse = new JSONObject();
		/*
		 * if(!em.isOpen()){ em = emf.createEntityManager(); }
		 */

		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;

		try {

			logger.info("--------------------------GUEST OPEN----------------------------");
			logger.info("-------Input: driverId:" + driverId + " tripId: " + tripId + " Time: " + time + " lat:" + lat
					+ "lng:" + lng);

			et = em.getTransaction();
			et.begin();

			int trpCurrentStatus = CommonQueries.getTripCurrentStatus(tripId, em);
			long deviceid = CommonQueries.getDeviceIdUsingDriverId(driverId, em);
			if (deviceid == -1) {
				responsse.put("message", "Error: Device Id not available against the Driver Id");
				responsse.put("statusCode", 500);
				responsse.put("tripcurrentstatus", trpCurrentStatus);
				ret = responsse.toString();
				return ret;
			}
			String imei = CommonQueries.getImeiUsingDeviceId(deviceid, em);
			logger.info("----IMEI: " + imei);

			trip = CommonQueries.getTripUsingTripId(tripId, em);
			if (trip != null) {
				if (trip.getStatus() == 1 && trip.getGRC() == null) {
					ecio = CommonQueries.getEciousingTrip(tripId, em);
					vehicle = getVehicleUsingVehicleId(trip.getVehicle().getVehicleId(), em);

					String sourceLatLng = CommonQueries.getLatLngFromGarage(vehicle.getGarage().getGarageId(), em);
					String destinationLatLong = lat + " " + lng;
					double checkinKm = getDistanceUsingLatLong(sourceLatLng, destinationLatLong);
					logger.info("checkinKm calculated in guestOpen: "+ checkinKm);
					logger.info("Garage to pickup Time duration: "+ durationHours + " hours " + durationMinutes +" minutes");
					
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
					Date dt = new Date(time);
					SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");  
					String timeString = simpleDateFormat.format(dt);
					LocalDateTime dateTime = LocalDateTime.parse(timeString,formatter);
					dateTime = (dateTime.minusHours(durationHours)).minusMinutes(durationMinutes);
					String afterSubtraction = dateTime.format(formatter);
					Date groDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(afterSubtraction);
					logger.info("groDateTime " +groDateTime);
					// vehicle = getVehicleUsingimei(imei, em);
					// if event=start update checkin in ecio and busy=1 in
					// vehicle
					if (ecio == null || vehicle == null) {
						responsse.put("message", "Error: EmployeeCheckInOut or Vehicle is null");
						responsse.put("statusCode", 500);
						responsse.put("tripcurrentstatus", trpCurrentStatus);
						ret = responsse.toString();
						return ret;
					} else {
						float chekcinLat = new Double(lat).floatValue();
						float chekcinLng = new Double(lng).floatValue();
						ecio.setCheckin(new Date(time));
						ecio.setCheckinKm(checkinKm);
						ecio.setCheckinLatitude(chekcinLat);
						ecio.setCheckinLongitude(chekcinLng);
						trip.setLastTimeStamp(System.currentTimeMillis());
						trip.setGRO(groDateTime);
						vehicle.setBusy(true);
						em.merge(ecio);
						em.merge(trip);
						em.merge(vehicle);
						et.commit();
						responsse.put("message", "Success");
						responsse.put("statusCode", 200);
						responsse.put("tripcurrentstatus", trpCurrentStatus);
						ret = responsse.toString();
						callDirectUrl();
					}
				} else {
					logger.info("---------Trip is Inactive");
					responsse.put("message", "Inactive");
					responsse.put("statusCode", 500);
					responsse.put("tripcurrentstatus", 6);
					ret = responsse.toString();
					return ret;
				}
			} else {
				logger.info("---------tripId is wrong: " + tripId);
				responsse.put("message", "trip is wrong");
				responsse.put("statusCode", 500);
				responsse.put("tripcurrentstatus", trpCurrentStatus);
				ret = responsse.toString();
			}
			
			if (responsse.containsValue(200) && responsse.containsValue("Success")) {
				et.begin();
				trip.setTripCurrentStatus(2);
				em.merge(trip);
				et.commit();
			}

		} catch (Exception e) {
			responsse.put("message", "Error: Please try again");
			responsse.put("statusCode", 500);
			responsse.put("tripcurrentstatus", 0);
			ret = responsse.toString();
			// ret = "Error, Please try again";
			logger.error("Exception in  Guest Open :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			if (et.isActive()) {
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		} finally {
			if (em.isOpen()) {
				em.close();
			}
		}
		logger.info("Response----------------" + ret);
		return ret;
	}

	// calculateInvoice
	@SuppressWarnings("unchecked")
	@GET
	@Path("/GuestClose")
	@Consumes({ "multipart/related,text/plain" })
	public String guestClose(@QueryParam("tripId") long tripId, @QueryParam("driverId") long driverId,
			@QueryParam("time") long time, @QueryParam("parkingAmount") Double parkingAmount,
			@QueryParam("tollAmount") Double tollAmount, @QueryParam("payStatus") int payStatus,
			@QueryParam("lat") double lat, @QueryParam("lng") double lng) throws Exception {
		String response = "", invGenerateFor = "", rateTypeName = "", totalAmt = "";
		boolean isInvoiceGen = true, foundSameBase = false;
		boolean generatePdf = false, isInvGenerate = true;
		double totalKms = 0, amount = 0, flatAmount = 0, preTaxAmount = 0, taxAmount = 0;
		long totalMins = 0;
		Date fromDate = null, toDate = null;
		int billingRuleType = 0;

		JSONObject jsonObj = new JSONObject();

		/*
		 * if(!em.isOpen()){ em = emf.createEntityManager(); }
		 */
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;

		try {
			logger.info("--------------------------GUEST CLOSE-----------------------");
			logger.info("--------------------------- tripId : " + tripId + " driverId: " + driverId + " time : " + time
					+ " parkingAmount : " + parkingAmount + " tollAmount : " + tollAmount + " payStatus : " + payStatus
					+ " lat : " + lat + " lng : " + lng);
			logger.info("----event cordinates -- lat : " + lat + " lng " + lng);
			logger.info("-------- Payment Status: " + payStatus);

			//callFCURL(tripId);

			et = em.getTransaction();
			et.begin();

			int trpCurrentStatus = CommonQueries.getTripCurrentStatus(tripId, em);
			long deviceid = CommonQueries.getDeviceIdUsingDriverId(driverId, em);
			if (deviceid == -1) {
				jsonObj.put("message", "Error: Device Id not available against the Driver Id");
				jsonObj.put("statusCode", 500);
				jsonObj.put("tripcurrentstatus", trpCurrentStatus);
				response = jsonObj.toString();
				return response;
			}

			String imei = CommonQueries.getImeiUsingDeviceId(deviceid, em);
			logger.info("------IMEI : " + imei);

			trip = CommonQueries.getTripUsingTripId(tripId, em);
			if (trip != null) {
				if (trip.getStatus() == 1 && trip.getGRC() == null) {
					ecio = CommonQueries.getEciousingTrip(tripId, em);
					
					vehicle = getVehicleUsingVehicleId(trip.getVehicle().getVehicleId(), em);
					// vehicle = getVehicleUsingimei(imei, em);
					br = trip.getBookingReq();
					Tariff tariff = CommonQueries.getTariff(tripId, em);
					Employee emp = ecio.getEmployee();

					logger.info("\n\n\n \t\t\t booking req---------" + br.getBookingReqId());
					
					// Calculate total Kms.
					totalKms = ecio.getCheckoutKm() - ecio.getCheckinKm();

					/*
					 * 1) Check whether Invoice need to be Generate or not.
					 * Check whether Service Type is Outstation or not, If
					 * Outstation no need to Generate Invoice. If condition for
					 * Corporate customer, else condition for Retail customer.
					 */
					rateTypeName = tariff.getServiceType().getRateType().getRateTypeName();

					logger.info("-----------------Rate Type is: " + rateTypeName);

					OrgRateTypeInvoiceAssoc orgRTInvAsosc = CommonQueries.getInvoiceGenerateFlag(
							tariff.getBase().getOrg().getOrganisationId(),
							tariff.getServiceType().getRateType().getRateTypeId(), em);

					if (orgRTInvAsosc != null) {
						isInvGenerate = orgRTInvAsosc.isGenerateInvoice();
					}

					if (isInvGenerate) {
						if (!WSConstants.OUTSTATION.equals(rateTypeName)) {

							if (emp.getCorporate() != null && tariff.getCorporate() != null) {
								logger.info("********Corporate Customer********");
								invGenerateFor = "corporate";
								// Check emp's corporate and tariff's corporate
								// is same or not.
								if (emp.getCorporate().getCorporateId() == tariff.getCorporate().getCorporateId()) {

									OrgCorporateAssoc ocAssoc = CommonQueries.getOrgCorpAssoc(
											tariff.getCorporate().getCorporateId(),
											tariff.getBase().getOrg().getOrganisationId(), em);

									// considerServTax = ocAssoc.isServiceTax();

									List<BillingRule> rulelist = CommonQueries.getBillingRuleList(
											tariff.getCorporate().getCorporateId(),
											tariff.getBase().getOrg().getOrganisationId(), em);
									if (rulelist.size() != 0) {

										for (BillingRule billingrule : rulelist) {

											// here consider only those billing
											// rule those rate type is same as
											// booking request rate type.
											if (billingrule.getRateType().getRateTypeName()
													.equals(br.getSerType().getRateType().getRateTypeName())) {
												logger.info("\n\n\n \t\t\t billing rule type--------:"
														+ billingrule.getRuleType());
												billingRuleType = billingrule.getRuleType();
												if (billingrule.getRuleType() == 1 || billingrule.getRuleType() == 3) {

													isInvoiceGen = false;

													logger.info("invoice generate if GRC---------:" + isInvoiceGen);
												} else {

													isInvoiceGen = CommonQueries.getInvGenFlag(
															emp.getCorporate().getCorporateId(),
															tariff.getBase().getOrg().getOrganisationId(), em);
													logger.info(
															"Invoice generate if emp and tariff corporate are same--------------:"
																	+ isInvoiceGen);

													if (billingrule.getRateType().getRateTypeName()
															.equals(br.getSerType().getRateType().getRateTypeName())) {
														billingruleForInvoice = billingrule;
														logger.info(
																"\n\n\n \t\t\t billing rule for invoice------rule Id is:"
																		+ billingruleForInvoice.getRuleId());
														HashMap<Integer, String> ruletypehm = AdCommon.getRuleType();
														String value1 = ruletypehm.get(billingrule.getRuleType());
														logger.info("\n\n\n \t\t\t value1--------------:" + value1);

														if (value1.equals("GSO to GSC")) {
															fromDate = ecio.getCheckin();
															toDate = ecio.getCheckout();
														} else if (value1.equals("GRO to GSC")) {
															fromDate = trip.getGRO();
															toDate = ecio.getCheckout();
														}
													}

												}
											}

										}

									} else {
										logger.info("*******billing rule list is null.");
										if (emp.getCorporate().getCorporateId() == tariff.getCorporate()
												.getCorporateId()) {
											isInvoiceGen = CommonQueries.getInvGenFlag(
													tariff.getCorporate().getCorporateId(),
													tariff.getBase().getOrg().getOrganisationId(), em);
											logger.info(
													"Invoice generate if emp and tariff corporate are same--------------"
															+ isInvoiceGen);
										}
									}
								} else {
									logger.info(
											"Employee's corporate and tariff's corporate are not same!!!!!!!!!!!!!");
								}

							} else if (emp.getCorporate() == null || tariff.getCorporate() == null) {
								logger.info("********Retail Customer********");
								isInvoiceGen = true; // invoice generation is
														// true if emp is not
														// associated with any
														// corporate
								invGenerateFor = "retail";
								logger.info("Invoice generate for retail customer--------------" + isInvoiceGen);
							}
						} else {
							logger.info("\n ---------For Outstation, Invoice not generated.");
							isInvoiceGen = false;
						}
						// Calculate Total Minutes.
						if (fromDate != null && toDate != null) {
							totalMins = calculateTimeDifference(fromDate, toDate);
							// noOfDays = getNoOfDays(fromDate, toDate);
						} else {
							totalMins = calculateTimeDifference(ecio.getCheckin(), new Date(time));
							// noOfDays = getNoOfDays(ecio.getCheckin(), new
							// Date(time));
						}
						logger.info("---Total Kms: " + totalKms + " Total Minutes: " + totalMins);

						// madhavi code start

						boolean myInvoiceGenFlag = false;
						logger.info("-------------Rate Type is: " + rateTypeName);

						if (!WSConstants.OUTSTATION.equals(rateTypeName)) {
							logger.info("------------- Payment option: " + br.getPaymentOption());
							if (br.getPaymentOption() == 1) { // its CASH
								logger.info("------------- Billing Rule type: " + billingRuleType);
								if ((billingRuleType == 0) || (billingRuleType == 2)) {
									myInvoiceGenFlag = true;
								}
							}
						}
						logger.info("-------------Invoice gen flag: " + myInvoiceGenFlag);

						// madhavi code end

						// 2) Check the isInvoiceGen flag value, If TRUE means
						// invoice generate else FALSE means invoice not
						// generate.
						// if(isInvoiceGen){
						if (myInvoiceGenFlag) {
							// logger going to generate invoice
							String res = new Invoice().createInvoice(totalKms, totalMins, trip, billingruleForInvoice,
									tollAmount, parkingAmount, rateTypeName, 0, "", tariff, invGenerateFor, em);

							if (!"Error".equalsIgnoreCase(res)) {

								JSONParser parser = new JSONParser();

								JSONObject jsonRes = (JSONObject) parser.parse(res);

								String r = jsonRes.get("Response").toString();

								totalAmt = jsonRes.get("TotalAmount").toString();

								jsonObj = jsonRes;

								String isInvoiceGenerate = String.valueOf(myInvoiceGenFlag);
								// isInvoiceGenerate=String.valueOf(isInvoiceGen);

								jsonObj.put("InvoiceGenerate", isInvoiceGenerate);
								jsonObj.put("message", "Success");
								jsonObj.put("statusCode", 200);
								jsonObj.put("tripcurrentstatus", trpCurrentStatus);
								response = jsonObj.toString();

								if ("Success".equals(r)) {
									if (br.getPaymentOption() != 2) {
										logger.info("Vehicle---------" + vehicle.getVehicleId());
										logger.info("Employee---------" + ecio.getEmployee().getName()
												+ " :Vendor-------" + trip.getVehicle().getVendor().getMobile());
										logger.info("Invoice Generate For: ---------" + invGenerateFor);

										logger.info("--------Billing Rule Type is: " + billingRuleType);

										// following check is unnecessary
										// -Madhavi

										if ((billingRuleType == 0) || (billingRuleType == 2)) {
											// sendSMSToUserOnPaymentType(ecio.getEmployee(),
											// trip.getBase(),
											// Double.parseDouble(totalAmt));
											// AdCommon.generateInvoiceReport(tripId,
											// trip, ecio, invGenerateFor,
											// br.getPaymentOption(),
											// billingRuleType, -1);
											generatePdf = true;
											logger.info("--------Email and SMS is sent.");
										} else {
											String formattedTotalKm = new DecimalFormat("###0.00").format(totalKms);
											logger.info(
													"--------Email and SMS is NOT sent  when myInvoiceGenFlag= true.. howz that possible ? -MB");
											jsonObj.put("Response", "successwithnoinvoice");
											jsonObj.put("TotalKms", formattedTotalKm);
											jsonObj.put("TotalMins", totalMins);
											jsonObj.put("TotalAmount", "0.00");
											jsonObj.put("InvoiceGenerate", "false");
											jsonObj.put("message", "successwithnoinvoice");
											jsonObj.put("statusCode", 200);
											jsonObj.put("tripcurrentstatus", trpCurrentStatus);
											response = jsonObj.toString();
										}
									} else {
										String formattedTotalKm = new DecimalFormat("###0.00").format(totalKms);
										jsonObj.put("Response", "successwithnoinvoice");
										jsonObj.put("TotalKms", formattedTotalKm);
										jsonObj.put("TotalMins", totalMins);
										jsonObj.put("TotalAmount", "0.00");
										jsonObj.put("InvoiceGenerate", "false");
										jsonObj.put("message", "successwithnoinvoice");
										jsonObj.put("statusCode", 200);
										jsonObj.put("tripcurrentstatus", trpCurrentStatus);
										response = jsonObj.toString();
									}
								}

							}
						} else {
							String formattedTotalKm = new DecimalFormat("###0.00").format(totalKms);
							jsonObj.put("Response", "successwithnoinvoice");
							jsonObj.put("TotalKms", formattedTotalKm);
							jsonObj.put("TotalMins", totalMins);
							jsonObj.put("TotalAmount", "0.00");
							jsonObj.put("InvoiceGenerate", "false");
							jsonObj.put("message", "successwithnoinvoice");
							jsonObj.put("statusCode", 200);
							jsonObj.put("tripcurrentstatus", trpCurrentStatus);
							response = jsonObj.toString();
						}

					} else {
						String formattedTotalKm = new DecimalFormat("###0.00").format(totalKms);
						jsonObj.put("Response", "successwithnoinvoice");
						jsonObj.put("TotalKms", formattedTotalKm);
						jsonObj.put("TotalMins", totalMins);
						jsonObj.put("TotalAmount", "0.00");
						jsonObj.put("InvoiceGenerate", "false");
						jsonObj.put("message", "successwithnoinvoice");
						jsonObj.put("statusCode", 200);
						jsonObj.put("tripcurrentstatus", trpCurrentStatus);
						response = jsonObj.toString();
					}

					if (trip == null || ecio == null || vehicle == null) {
						jsonObj.put("Response", "Trip, Vehicle or EmployeeCheckInOut is invalid");
						jsonObj.put("TotalKms", "0.00");
						jsonObj.put("TotalMins", 0);
						jsonObj.put("TotalAmount", "0.00");
						jsonObj.put("InvoiceGenerate", "false");
						jsonObj.put("message", "Trip, Vehicle or EmployeeCheckInOut is invalid");
						jsonObj.put("statusCode", 500);
						jsonObj.put("tripcurrentstatus", trpCurrentStatus);
						response = jsonObj.toString();
						response = "Error";
					} else {

						ecio.setCheckout(new Date(time));
						float chekcOutLat = new Double(lat).floatValue();
						float chekcOutLng = new Double(lng).floatValue();
						ecio.setCheckoutLatitude(chekcOutLat);
						ecio.setCheckoutLongitude(chekcOutLng);
						trip.setTollAmount(tollAmount);
						trip.setParkingAmount(parkingAmount);
						trip.setLastTimeStamp(System.currentTimeMillis());
						if (payStatus == 3) {
							trip.setPaymentStatus(payStatus);
						}
						vehicle.setBusy(false);
						em.merge(vehicle);
						EmployeeCheckInOut addedEcio = em.merge(ecio);
						Trip addedTrip = em.merge(trip);
						et.commit();

						if (generatePdf) {
							sendSMSToUserOnPaymentType(addedEcio.getEmployee(), addedTrip.getBase(),
									Double.parseDouble(totalAmt), em);
							AdCommon.generateInvoiceReport(tripId, addedTrip, addedEcio, invGenerateFor,
									br.getPaymentOption(), billingRuleType, -1, em);
						}

						callDirectUrl();
						// OrgBase
						// selectedBase=vehicle.getServiceprovider().getBase();
						/*
						 * List<OrgBase>
						 * baseList=ecio.getEmployee().getBaseList();
						 * for(OrgBase base:baseList){
						 * if(base.getBaseName().equals(selectedBase.getBaseName
						 * ())){
						 * logger.info("Base---------------"+base.getBaseName()
						 * +"  selectedbase-----"+selectedBase.getBaseName());
						 * foundSameBase=true; } }
						 * 
						 * if(!foundSameBase){
						 * logger.info("--not found-----------------------");
						 * baseList.add(selectedBase);
						 * logger.info("\n\n\n \t\t\t baseList----------------"
						 * +baseList.size()+" addedBase-----------"+selectedBase
						 * .getBaseName()); et.begin();
						 * ecio.getEmployee().setBaseList(baseList);
						 * et.commit(); }
						 */
					}
				} else {
					logger.info("---------Trip is Inactive");
					jsonObj.put("Response", "inactive");
					jsonObj.put("TotalKms", "0.00");
					jsonObj.put("TotalMins", 0);
					jsonObj.put("TotalAmount", "0.00");
					jsonObj.put("InvoiceGenerate", "false");
					jsonObj.put("message", "inactive");
					jsonObj.put("statusCode", 500);
					jsonObj.put("tripcurrentstatus", 6);
					response = jsonObj.toString();
				}
			} else {
				logger.info("---------TripId is wrong: " + tripId);
				jsonObj.put("Response", "trip is wrong");
				jsonObj.put("TotalKms", "0.00");
				jsonObj.put("TotalMins", 0);
				jsonObj.put("TotalAmount", "0.00");
				jsonObj.put("InvoiceGenerate", "false");
				jsonObj.put("message", "trip is wrong");
				jsonObj.put("statusCode", 500);
				jsonObj.put("tripcurrentstatus", trpCurrentStatus);
				response = jsonObj.toString();
			}

			
			if (jsonObj.containsValue(200) && jsonObj.containsValue("Success")
					|| jsonObj.containsValue("successwithnoinvoice")) {				
				et.begin();
				trip.setTripCurrentStatus(4);
				em.merge(trip);
				et.commit();
			}
		} catch (Exception e) {
			jsonObj.put("Response", "Error: Please try again.");
			jsonObj.put("TotalKms", "0.00");
			jsonObj.put("TotalMins", 0);
			jsonObj.put("TotalAmount", "0.00");
			jsonObj.put("InvoiceGenerate", "false");
			jsonObj.put("message", "Error: Please try again.");
			jsonObj.put("statusCode", 500);
			jsonObj.put("tripcurrentstatus", 0);
			response = jsonObj.toString();
			// response = "Error, Please try again.";
			logger.error("*****Exception in  Guest Close :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			if (et.isActive()) {
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		} finally {
			if (em.isOpen()) {
				em.close();
			}
		}

		logger.info("--Invoice Response----: " + response);

		return response;
	}

	// @GET
	// @Path("/CollectAmount")
	// @Consumes({"multipart/related,text/plain"})
	@POST
	@Path("/TripEnd")
	public String tripEnd(String jsonString) {
		String ret = null, invGenerateFor = "";
		JSONObject response = new JSONObject();
		// boolean considerServTax = true;
		int billingRuleType = 0;
		/*
		 * if(!em.isOpen()){ em = emf.createEntityManager(); }
		 */
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;
		try {
			logger.info("-------------TRIP END---------------------------");
			logger.info("Json------------------" + jsonString);

			et = em.getTransaction();
			et.begin();

			try {
				jsonObj = (JSONObject) new JSONParser().parse(jsonString.toString());
			} catch (ParseException e) {
				logger.error("------Excpetion:" + e.getMessage(), e);
			}

			String tripId = (String) jsonObj.get("tripId");
			long tripid = Long.parseLong(tripId);
			String otpStatus = (String) jsonObj.get("otpStatus");
			int otpstatus = Integer.parseInt(otpStatus);
			String custSignUrl = (String) jsonObj.get("custSignUrl");

			long driverId = (long) jsonObj.get("driverId");

			int trpCurrentStatus = CommonQueries.getTripCurrentStatus(tripid, em);
			long deviceid = CommonQueries.getDeviceIdUsingDriverId(driverId, em);
			if (deviceid == -1) {

				response.put("message", "Error: Device Id not available against the Driver Id");
				response.put("statusCode", 500);
				response.put("tripcurrentstatus", trpCurrentStatus);
				ret = response.toString();
				return ret;
			}
			String imei = CommonQueries.getImeiUsingDeviceId(deviceid, em);
			// String imei=(String) jsonObj.get("imei");
			String formName = (String) jsonObj.get("formName");
			String timee = (String) jsonObj.get("time");
			long time = Long.parseLong(timee);
			// String km=(String) jsonObj.get("grcKm");
			// double grckm=Double.parseDouble(km);

			String tollAmt = (String) jsonObj.get("tollAmt");
			if(tollAmt == "")
				tollAmt = "0";
			double tollAmount = Double.parseDouble(tollAmt);
			String parkingAmt = (String) jsonObj.get("parkingAmt");
			double parkingAmount = Double.parseDouble(parkingAmt);

			Object latObj = jsonObj.get("lat");
			Object lngObj = jsonObj.get("lng");
			float lat = 0;
			float lng = 0;
			if (latObj != null) {
				lat = Float.parseFloat(latObj.toString());
				lng = Float.parseFloat(lngObj.toString());
			}

			logger.info("tripId-----------" + tripId + "  otp status---------" + otpStatus);

			trip = CommonQueries.getTripUsingTripId(tripid, em);
			if (trip.getStatus() == 1 && trip.getGRC() == null) {
				ecio = CommonQueries.getEciousingTrip(tripid, em);
				vehicle = getVehicleUsingVehicleId(trip.getVehicle().getVehicleId(), em);
				br = trip.getBookingReq();
				logger.info("booking request-------------" + br.getBookingReqId());
				Tariff tariff = CommonQueries.getTariff(tripid, em);
				Employee emp = ecio.getEmployee();

				double totalKm = 0;
				long totalMin = 0;
				Date fromDate = null, toDate = null;
				//double grckm = trip.getGrcKm();
				
				//calculate grcKms
				if(lat == 0.0 || lng == 0.0){
					lat = trip.getGrcLatitude();
					lng = trip.getGrcLongitude();
				}
				String sourceLatLng = lat + " " + lng;
				logger.info("tripend lat: "+ lat + " lng: " + lng);
				String destinationLatLong = CommonQueries.getLatLngFromGarage(vehicle.getGarage().getGarageId(), em);
				double checkoutToGarageKm = getDistanceUsingLatLong(sourceLatLng, destinationLatLong);
				logger.info("Drop to Garage Time duration: "+ durationHours + " hours " + durationMinutes +" minutes");
				logger.info("checkoutToGarageKm in tripEnd::: "+ checkoutToGarageKm);
				double grckm = ecio.getCheckoutKm()+checkoutToGarageKm;
				Date grcDateTime = new Date(time);
				grcDateTime = DateUtils.addHours(grcDateTime, durationHours);
				grcDateTime = DateUtils.addMinutes(grcDateTime, durationMinutes);
				logger.info("grcDateTime "+ grcDateTime);
				String rateTypeName = tariff.getServiceType().getRateType().getRateTypeName();

				logger.info("++++++++++ Rate type name: " + rateTypeName);

				if (trip.getBookingReq().getBookingTime().getTime() > System.currentTimeMillis()) {

					logger.info("cannot end trip before reporting time  ......... repoting time "
							+ trip.getBookingReq().getBookingTime().toString());

					response.put("message", "Error: Cannot end trip before reporting time");
					response.put("statusCode", 500);
					response.put("tripcurrentstatus", trpCurrentStatus);
					ret = response.toString();
					return ret;
				}
				if (!WSConstants.OUTSTATION.equals(rateTypeName)) {

					if (emp.getCorporate() != null && tariff.getCorporate() != null) {
						invGenerateFor = "corporate";

						if (CommonQueries.getInvGenFlag(emp.getCorporate().getCorporateId(),
								tariff.getBase().getOrg().getOrganisationId(), em)) {

							OrgCorporateAssoc ocAssoc = CommonQueries.getOrgCorpAssoc(
									tariff.getCorporate().getCorporateId(),
									tariff.getBase().getOrg().getOrganisationId(), em);

							// considerServTax = ocAssoc.isServiceTax();

							List<BillingRule> rulelist = CommonQueries.getBillingRuleList(
									tariff.getCorporate().getCorporateId(),
									tariff.getBase().getOrg().getOrganisationId(), em);

							if (rulelist.size() != 0) {

								for (BillingRule billingrule : rulelist) {

									if (billingrule.getRateType().getRateTypeName()
											.equals(br.getSerType().getRateType().getRateTypeName())) {
										billingRuleType = billingrule.getRuleType();
										if (billingrule.getRuleType() == 1) {

											double grokm = trip.getGroKm();

											totalKm = grckm - grokm;

											fromDate = trip.getGRO();
											toDate = new Date(time);

											totalMin = calculateTimeDifference(fromDate, toDate);

											String res = new Invoice().createInvoice(totalKm, totalMin, trip,
													billingrule, tollAmount, parkingAmount, rateTypeName, 0, "", tariff,
													invGenerateFor, em);

										} else if (billingrule.getRuleType() == 3) {

											double gsokm = ecio.getCheckinKm();

											totalKm = grckm - gsokm;

											fromDate = ecio.getCheckin();
											toDate = new Date(time);

											totalMin = calculateTimeDifference(fromDate, toDate);

											String res = new Invoice().createInvoice(totalKm, totalMin, trip,
													billingrule, tollAmount, parkingAmount, rateTypeName, 0, "", tariff,
													invGenerateFor, em);
											logger.info("------------" + res);
										} else if (billingrule.getRuleType() == 4) {

											double gsckm = ecio.getCheckoutKm();
											double groKm = trip.getGroKm();

											totalKm = gsckm - groKm;

											fromDate = trip.getGRO();
											toDate = new Date(time);

											totalMin = calculateTimeDifference(fromDate, toDate);

											String res = new Invoice().createInvoice(totalKm, totalMin, trip,
													billingrule, tollAmount, parkingAmount, rateTypeName, 0, "", tariff,
													invGenerateFor, em);
											logger.info("------------" + res);
										}

									}

								}

							}

							ih = CommonQueries.getInvoiceHeaderUsingTripId(tripid, em);
							logger.info("++++++++++ Invoice generated for Corporate customer.");

						} else {

							logger.info("++++++++++ Invoice not generated for Corporate customer.");
						}
					} else {
						invGenerateFor = "retail";
						ih = CommonQueries.getInvoiceHeaderUsingTripId(tripid, em);
						logger.info("++++++++++ Invoice generated for Retail customer.");
					}
				}

				String driverName = trip.getDriver().getName();
				Vendor vendor = trip.getDriver().getVendor();
				Employee userName = ecio.getEmployee();
				// 1- Cash, 2- Valid OTP, 3 - Invalid OTP
				if (trip != null) {

					int paymentStatus = 0;
					if ((custSignUrl != null) && !(custSignUrl.isEmpty())) {
						logger.info("cust sign available --- calling writeCustomerSignature for trip " + tripid);
						paymentStatus = 2;

						String sign = writeCustomerSignature(custSignUrl, tripid);
						if (sign == null) {
							response.put("message", "Error: Unsuccessful as no Customer signature");
							response.put("statusCode", 500);
							response.put("tripcurrentstatus", trpCurrentStatus);
							ret = response.toString();
							return ret;
						}

					}

					if (ih != null) {
						if ((billingRuleType == 0) || (billingRuleType == 2)) {
							double cashAmount = ih.getTotalAmount();
							paymentStatus = 1;
							logger.info("---------------Sending SMS of Cash Collected");
							sendSMSToSPOnCashCollected(userName, tariff.getBase(), cashAmount, trip.getDriver(), em);
						} else {
							logger.info("---------Billing Rule Type is: " + billingRuleType + " : no sms send");
						}
					}

					// et.begin();
					trip.setPaymentStatus(paymentStatus);
					float grcLat = new Double(lat).floatValue();
					float grcLng = new Double(lng).floatValue();
					trip.setGRC(grcDateTime);
					trip.setGrcKm(grckm);
					trip.setGrcLatitude(grcLat);
					trip.setGrcLongitude(grcLng);
					// if(billingRuleType == 1 || billingRuleType == 3 ||
					// billingRuleType == 4){
					trip.setTollAmount(tollAmount);
					trip.setParkingAmount(parkingAmount);
					// }
					br.setStatus(5); // closed
					trip.setLastTimeStamp(System.currentTimeMillis());
					trip.setDutyStatus("Digital");
					em.merge(trip);
					em.merge(br);
					et.commit();
					removeFromDevice(imei, formName);
					sendSMSToUserforFeedback(ecio.getEmployee(), tariff.getBase(), em);

					response.put("message", "Success");
					response.put("statusCode", 200);
					response.put("tripcurrentstatus", trpCurrentStatus);
					ret = response.toString();
					callDirectUrl();

					logger.info("--------- Calling broker web service --------------");

					if ((brokerwsURL == null) || (brokerwsUserName == null) || (brokerwsPassword == null)) {

						Properties props = AdCommon.getPropFromDriverAppWS();
						brokerwsURL = props.getProperty("brokerwsURL");
						brokerwsUserName = props.getProperty("brokerwsUserName");
						brokerwsPassword = props.getProperty("brokerwsPassword");

					}

					if (CommonQueries.isBWSCallValidIfLTBooking(trip, em)) {
						AdCommon.callBrokerWebService(trip, WSConstants.OBE_EVENT_INVOICE, brokerwsURL,
								brokerwsUserName, brokerwsPassword, em);
					}

				} else {
					response.put("message", "Error");
					response.put("statusCode", 500);
					response.put("tripcurrentstatus", trpCurrentStatus);
					ret = response.toString();
				}
			} else {
				logger.info("---------Trip is Inactive");
				response.put("message", "inactive");
				response.put("statusCode", 500);
				response.put("tripcurrentstatus", 6);
				ret = response.toString();
			}

			if (jsonObj.containsValue(200) && jsonObj.containsValue("Success")) {
				et.begin();
				trip.setTripCurrentStatus(6);
				em.merge(trip);
				et.commit();
			}

		} catch (Exception e) {
			response.put("message", "Error: Please try again");
			response.put("statusCode", 500);
			response.put("tripcurrentstatus", 0);
			ret = response.toString();
			// ret = "Error, Please try again";
			logger.error("Exception in  Trip End :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			if (et.isActive()) {
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		} finally {
			if (em.isOpen()) {
				em.close();
			}
		}
		logger.info("-------- Response : " + ret);
		return ret;
	}

	// Following web service is used for get the Driver last 10 trips details.
	// Here if Vehicle Registration No. is NA means don`t consider vehicle id in
	// the query.
	@SuppressWarnings({ "unchecked" })
	@GET
	@Path("/getdrivertrips")
	@Consumes({ "multipart/related,text/plain" })
	public String getLastTripsOfDriver(@QueryParam("driverId") String driverId,
			@QueryParam("noOfTrips") String noOfTrips, @QueryParam("vehId") String vehId) {

		logger.info("....................getdrivertrips............ " + " driverId : " + driverId + " noOfTrips : "
				+ noOfTrips + " vehId : " + vehId);

		logger.info("++++++++++++++++Inside getLastTripsOfDriver");
		logger.info(
				"++++++++++++++++Input:Driver ID: " + driverId + " No.Of Trips: " + noOfTrips + " Veh ID: " + vehId);

		String lastTrips = null;
		List<Trip> trips = null;
		EntityManager em = emf.createEntityManager();
		try {
			/*
			 * if(!em.isOpen()){ em = emf.createEntityManager(); }
			 */
			// Booking Request Status 1- new, 2- Cancelled, 3-Active, 4.Expired,
			// 5.Closed, 6. Deleted
			if ("NA".equalsIgnoreCase(vehId)) {
				String queryString = "select * FROM Trip t , BookingRequest br where t.driverId=" + driverId
						+ " and br.status = 5 and t.bookingReqId = br.bookingReqId order by tripId desc";
				Query query = em.createNativeQuery(queryString, Trip.class);
				query.setMaxResults(Integer.parseInt(noOfTrips));
				trips = query.getResultList();
			} else {
				String queryString = "select * FROM Trip t , BookingRequest br where t.driverId=" + driverId
						+ " and t.vehicleId=" + vehId
						+ " and br.status = 5 and t.bookingReqId = br.bookingReqId order by tripId desc";
				Query query = em.createNativeQuery(queryString, Trip.class);
				query.setMaxResults(Integer.parseInt(noOfTrips));
				trips = query.getResultList();
			}

			if (trips != null && trips.size() > 0) {
				JSONObject json = new JSONObject();
				JSONArray jsonArray = new JSONArray();

				for (Trip trip : trips) {
					logger.info("-------- TripId : " + trip.getTripId() + " ------------BR Status : "
							+ trip.getBookingReq().getStatus());

					// if(trip.getBookingReq().getStatus() == 5){
					JSONObject jsonObj = new JSONObject();
					jsonObj.put("trip", trip.getTripId());
					jsonObj.put("date", new SimpleDateFormat("dd/MM/yyyy").format(trip.getReportingDate()));

					EmployeeCheckInOut ecio = CommonQueries.getEciousingTrip(trip.getTripId(), em);
					if (ecio != null) {
						jsonObj.put("guest", ecio.getEmployee().getName());
						if ((ecio.getCheckout() != null) && (ecio.getCheckin() != null)) {
							jsonObj.put("checkin", new SimpleDateFormat("hh:mm a").format(ecio.getCheckin()));
							if (ecio.getCheckout().after(ecio.getCheckin())) {

								long days = new Invoice().getNoOfDays(ecio.getCheckin(), ecio.getCheckout());
								days = days - 1;

								if (days > 1) {
									jsonObj.put("checkout", new SimpleDateFormat("hh:mm a").format(ecio.getCheckout())
											+ " +" + String.valueOf(days));
								} else {
									jsonObj.put("checkout", new SimpleDateFormat("hh:mm a").format(ecio.getCheckout()));
								}
							} else {
								jsonObj.put("checkout", new SimpleDateFormat("hh:mm a").format(ecio.getCheckout()));
							}
						} else {
							jsonObj.put("checkin", "NA");
							jsonObj.put("checkout", "NA");
						}
					} else {
						jsonObj.put("guest", "NA");
						jsonObj.put("checkin", "NA");
						jsonObj.put("checkout", "NA");
					}

					InvoiceHeader ih = CommonQueries.getInvoiceHeaderUsingTripId(trip.getTripId(), em);
					if (ih != null) {
						jsonObj.put("totalAmt", new DecimalFormat("###0.00").format(ih.getTotalAmount()));
						jsonObj.put("tollAmt", new DecimalFormat("###0.00").format(ih.getTollAmount()));
						jsonObj.put("parkingAmt", new DecimalFormat("###0.00").format(ih.getParkingAmount()));
						jsonObj.put("kms", new DecimalFormat("###0.00").format(ih.getKmsTravelled()));
					} else {
						jsonObj.put("totalAmt", "0.00");
						jsonObj.put("tollAmt", "0.00");
						jsonObj.put("parkingAmt", "0.00");
						jsonObj.put("kms", "0.00");
					}

					jsonObj.put("veh", trip.getVehicle().getRegNo());
					jsonObj.put("bookingReqNo", trip.getBookingReq().getBookingReqNum());

					jsonArray.add(jsonObj);
					// }
				}

				json.put("trips", jsonArray);

				lastTrips = json.toString();

			}
		} catch (NoResultException e) {
			logger.error("Exception in  getLastTripsOfDriver() :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			return lastTrips = null;
		} catch (Exception e) {
			logger.error("Exception in getLastTripsOfDriver() :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			return lastTrips = null;
		} finally {
			if (em.isOpen()) {
				em.close();
			}
		}
		logger.info("++++++++In Response: " + lastTrips);
		return lastTrips;
	}

	@POST
	@Path("/UploadImage")
	public String uploadImage(String jsonString) {

		logger.info(" UploadImage JSON ......... " + jsonString);
		logger.info("-------------UPLOAD IMAGE---------------------------");

		try {
			jsonObj = (JSONObject) new JSONParser().parse(jsonString.toString());
		} catch (ParseException e) {
			logger.error("------- JSON Parser Exception:", e);
		}

		String tripId = (String) jsonObj.get("tripId");
		long tripid = Long.parseLong(tripId);
		String image = (String) jsonObj.get("image");
		logger.info("-------- Image Data String length: " + image.length());

		String receiptImage = uploadReceipt(image, tripid);
		if (receiptImage == null) {
			logger.info("------- System error while storing the Image");
			return "Unsuccessful as no Receipt";
		}
		logger.info("---------- Image saved successfully");
		return "Success";
	}

	// updating VehicleDeviceLocation's gps data
	// @GET
	// @Path("/syncGPSData")
	// @Consumes({"multipart/related,text/plain"})

	@POST
	@Path("/syncGPSData")
	public String syncGPSData(String jsonString) {
		String ret = null;
		/*
		 * if(!em.isOpen()){ em = emf.createEntityManager(); }
		 */
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;
		try {
			logger.info("--------------SYNC GPS DATA------------------");
			logger.info("json---------------------" + jsonString);
			et = em.getTransaction();
			et.begin();
			try {
				jsonObj = (JSONObject) new JSONParser().parse(jsonString.toString());
				logger.info("json obj----------------" + jsonObj);

			} catch (ParseException e) {
				e.printStackTrace();
			}
			boolean alertSaved = false;
			if (jsonObj != null) {
				// gps
				float btry = 0.0f;

				if (jsonObj.containsKey("GPS")) {
					JSONArray gpsArray = (JSONArray) jsonObj.get("GPS");

					for (int i = 0; i < gpsArray.size(); i++) {
						JSONObject gpsObj = (JSONObject) gpsArray.get(i);
						VehicleDeviceLocationToGT vdl = new VehicleDeviceLocationToGT();

						String imei = (String) gpsObj.get("id");
						device = CommonQueries.getDeviceUsingImei(imei, em);

						Object latObj = gpsObj.get("mLatitude");
						String latStr = latObj.toString();
						float lat = Float.parseFloat(latStr);

						Object lngObj = gpsObj.get("mLongitude");
						String lngStr = lngObj.toString();
						float lng = Float.parseFloat(lngStr);

						long locTime = (long) gpsObj.get("mTime");

						Object speedObj = gpsObj.get("mSpeed");
						String speedStr = speedObj.toString();
						float speed = Float.parseFloat(speedStr);

						Object bearingObj = gpsObj.get("mBearing");
						String bearingStr = bearingObj.toString();
						float bearing = Float.parseFloat(bearingStr);

						/*
						 * Object elevationObj = gpsObj.get("mElevation");
						 * String elevationStr = elevationObj.toString(); float
						 * elevation = Float.parseFloat(elevationStr);
						 */

						float elevation = 0.0f;

						Object batteryObj = gpsObj.get("battery");

						String batteryStr = batteryObj.toString();

						logger.info("Battery level with GPS data ----------------" + batteryStr + " from " + imei);

						float battery = Float.parseFloat(batteryStr);
						btry = battery * 100;

						// String date =
						// "${year}.${month}.${day}.${hr}.${min}.${sec}";

						Date dateD = new Date(locTime);

						SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddhhmmss");
						formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
						String strDate = formatter.format(dateD);

						// GT Location format
						String locationString = "101AND" + imei + "," + strDate + "," + lng + "," + lat + "," + speed
								+ "," + bearing + "," + elevation + ",6,2,0,0," + battery + ",0\r\n";
						// et.begin();
						vdl.setLocationString(locationString);
						vdl.setStatus(WSConstants.LOCATION_STATUS_NEW);
						device.setLastSync(System.currentTimeMillis());
						vdl.setDevice(device);
						em.persist(vdl);
						// et.commit();
					}
					ret = "Success of GPS";
				} else if (jsonObj.containsKey("sync")) {

					JSONObject syncObj = (JSONObject) jsonObj.get("sync");

					String imei = (String) syncObj.get("id");
					device = CommonQueries.getDeviceUsingImei(imei, em);

					Object batteryObj = syncObj.get("battery");
					String batteryStr = batteryObj.toString();
					logger.info("Battery level with SYNC data ----------------" + batteryStr + " from " + imei);
					float battery = Float.parseFloat(batteryStr);
					btry = battery * 100;

					Object syncdata = syncObj.get("sync");
					String syncStr = syncdata.toString();
					double syncc = Double.parseDouble(syncStr);
					ret = "Success of Sync";
				}
				batteryy = getBatteryUsingDeviceId(device.getDeviceId(), em);
				float previousBatteryLevel = 0.0f;
				if (batteryy != null) {
					logger.info("Already present battery obect");

					previousBatteryLevel = batteryy.getBatteryLevel();
					logger.info("Previous battery level  ----------------" + previousBatteryLevel + " for device  "
							+ batteryy.getDevice().getSerialNumber());
					// et.begin();
					batteryy.setBatteryLevel(btry);
					// et.commit();
					// em.close();
					logger.info("Battery updated with ------------- " + btry + " for device "
							+ batteryy.getDevice().getSerialNumber());
				} else {
					logger.info("Battery recoed found in DB  for device " + device.getSerialNumber());
					Battery b = new Battery();
					// et.begin();
					b.setBatteryLevel(btry);
					b.setDevice(device);
					em.persist(b);
					logger.info("Created new battery record with battery level " + btry + " for device "
							+ device.getSerialNumber());
					// et.commit();
				}

				if ((btry < 30) && (previousBatteryLevel >= 30)) {
					Alert alert = new Alert();
					// et.begin();
					alert.setAlertType("batteryLow");
					alert.setSeverity("critical");
					alert.setMessage("Battery level at " + btry + "%");
					alert.setDevice(device);
					alert.setBase(device.getOrgBase());
					alert.setDate(new Date());
					device.setLastSync(System.currentTimeMillis());
					em.persist(alert);
					logger.info("old battery level  " + previousBatteryLevel + " and new battery level " + btry
							+ " for device " + device.getSerialNumber());
					logger.info("Created battery low alert................. " + alert.getAlertId() + " mesasge "
							+ alert.getMessage() + " severity " + alert.getSeverity() + " for device "
							+ device.getSerialNumber());
					alertSaved = true;
					// et.commit();
				} else {
					logger.info("old battery level  " + previousBatteryLevel + " and new battery level " + btry
							+ " not created battery low alert for device " + device.getSerialNumber());
				}
			}
			et.commit();
			if (alertSaved) {
				String url = AdCommon.createDirectUrl(directUrl);
				AdCommon.callDirectUrl(url);
			}
		} catch (Exception e) {
			logger.error("Exception in  Syng GPS Data :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			if (et.isActive()) {
				logger.info("-------- Before Rollback.");
				et.rollback();
				logger.info("-------- After Rollback.");
			}
		} finally {
			if (em.isOpen()) {
				em.close();
			}
		}

		return ret;
	}

	public String writeCustomerSignature(String custSign, long tripId) {
		File f = null;
		if (custSign != null && !custSign.equals("")) {

			BASE64Decoder decoder = new BASE64Decoder();
			byte[] decodedBytes = null;
			try {
				decodedBytes = decoder.decodeBuffer(custSign);
			} catch (IOException e) {
				e.printStackTrace();
			}
			logger.info("Decoded upload data : " + decodedBytes.length);

			String signatureLocation = (String) props.get("signatureLocation");
			String uploadFile = signatureLocation + "/sign_" + tripId + "_" + System.currentTimeMillis() + ".png";
			logger.info("Signature file : " + uploadFile);

			BufferedImage image = null;
			try {
				image = ImageIO.read(new ByteArrayInputStream(decodedBytes));
				logger.info("------------------image---------------------" + image);
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (image != null && image.equals("")) {

				logger.info("Buffered Image is null");
			} else {

				f = new File(uploadFile);
				try {
					if (f != null){
						ImageIO.write(image, "png", f);					}
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}

		} else {
			f = new File("/usr/gt/customerSignature/nosign.png");
			logger.info("Blank Singature ");
		}
		return custSign;

	}

	public String uploadReceipt(String image, long tripId) {
		File f = null;
		if (image != null && !image.equals("")) {

			BASE64Decoder decoder = new BASE64Decoder();
			byte[] decodedBytes = null;
			try {
				decodedBytes = decoder.decodeBuffer(image);
			} catch (IOException e) {
				e.printStackTrace();
			}
			logger.info("Decoded upload data : " + decodedBytes.length);
			String receiptLocation = (String) props.getProperty("receiptLocation");

			String uploadFile = receiptLocation + "/" + tripId + "_receipt_" + System.currentTimeMillis() + ".png";
			logger.info("Receipt File : " + uploadFile);

			BufferedImage image1 = null;
			try {
				image1 = ImageIO.read(new ByteArrayInputStream(decodedBytes));
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (image1 != null && image.equals("")) {
				logger.info("Buffered Image is null");
			} else {
				f = new File(uploadFile);
				try {
					if (f != null)
						ImageIO.write(image1, "png", f);
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}

		} else {
			f = new File("/usr/gt/Receipt/noreceipt.png");
			logger.info("Blank Receipt ");
		}
		return image;

	}

	private long calculateTimeDifference(Date Datein, Date Dateout) {
		logger.info("-----------Date in--------------" + Datein);
		logger.info("--------Date out-----------------" + Dateout);
		long totalMins = 0;
		try {

			// in milliseconds
			long diff = Dateout.getTime() - Datein.getTime();
			// logger.info("-----differnce in time------------"+diff);
			if (diff != 0) {
				long diffSeconds = diff / 1000 % 60;
				long diffMinutes = diff / (60 * 1000) % 60;
				long diffHours = diff / (60 * 60 * 1000) % 24;
				long diffDays = diff / (24 * 60 * 60 * 1000);

				logger.info(diffDays + " days, ");
				logger.info(diffHours + " hours, ");
				logger.info(diffMinutes + " minutes, ");
				logger.info(diffSeconds + " seconds.");
				if (diffSeconds > 0) {
					totalMins = (diffDays * 24 * 60) + (diffHours * 60) + diffMinutes + 1; // rounding
																							// to
																							// next
																							// minute
				} else {
					totalMins = (diffDays * 24 * 60) + (diffHours * 60) + diffMinutes;
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return totalMins;

	}

	// Following method is used for getting No. of Days between date range.
	/*
	 * private long getNoOfDays(Date Datein,Date Dateout){
	 * logger.info("-----------Date in--------------"+Datein);
	 * logger.info("--------Date out-----------------"+Dateout); long
	 * diffDays=0; try { //in milliseconds long diff = Dateout.getTime() -
	 * Datein.getTime(); if(diff!=0){ diffDays = diff / (24 * 60 * 60 * 1000);
	 * logger.info("++++++++++++++No. of Days: "+diffDays); } else{ diffDays =
	 * 1; } } catch (Exception e) {
	 * logger.info("++++++Exception in getNoOfDays: "+e.getMessage());
	 * e.printStackTrace(); } return diffDays; }
	 */

	public void removeFromDevice(String imei, String formName) {
		Process pr;
		String cmd;
		Runtime run = Runtime.getRuntime();

		try {
			String tripId = new Long(trip.getTripId()).toString();
			File dirExisting = new File(DEVICE_DATA_DIR + imei);
			String tripFile = DEVICE_DATA_DIR + imei + "/task_" + formName;
			logger.info("file name-----------" + tripFile);
			File fl1 = new File(tripFile);
			File fl2 = new File(tripFile + ".done");

			// Files.move(tripFile, tripFile + ".done");
			boolean fileMoved = fl1.renameTo(fl2);
			/*
			 * cmd = "touch " + DEVICE_DATA_DIR + imei + "/reconf"; pr =
			 * run.exec(cmd); pr.waitFor();
			 */
			logger.info("fileMoved=" + fileMoved + "--------------------");
		} catch (Exception e) {
			logger.info("unchanged---------------");
			e.printStackTrace();
		}
	}

	private void sendSMSToUserOnPaymentType(Employee user, OrgBase ob, double amount, EntityManager em) {
		try {
			logger.info("Sending SMS to User " + user.getName());
			String text = "";
			ServiceProviderApp spApp = AdCommon.getServiceProviderAppBySP(ob.getOrg().getOrganisationId(), em);

			String spName = "ODOCON";

			if (spApp != null) {
				spName = spApp.getOrg().getName();
			}
			text = "Dear " + user.getName() + ", Thank you for using " + ob.getOrg().getName() + ". Your Invoice for "
					+ amount + " has been posted on you user application. Team " + spName;

			logger.info("Message : " + text);
			String fromSMS = AdCommon.getFromSMSByBase(ob, em);
			AdCommon.insertSMSInDB(user.getMobile1(), text, fromSMS);

		} catch (Exception a) {
			// logger.error("Exception " + a.getMessage());
			logger.error(GTCommon.getStackTraceAsString(a));
		}
	}

	private void sendSMSToSPOnCashCollected(Employee user, OrgBase orgBase, double amount, Driver driver,
			EntityManager em) {
		try {
			logger.info("Sending SMS to Org Base " + orgBase.getBaseName());

			ServiceProviderApp spApp = AdCommon.getServiceProviderAppBySP(orgBase.getOrg().getOrganisationId(), em);

			String spName = "ODOCON";

			if (spApp != null) {
				spName = spApp.getOrg().getName();
			}
			String text = "";

			text = "Cash Collected -" + user.getName() + " - " + amount + " - " + driver.getName() + ", "
					+ driver.getMobile() + ". Team " + spName;

			logger.info("Message : " + text);
			String fromSMS = AdCommon.getFromSMSByBase(orgBase, em);
			AdCommon.insertSMSInDB(orgBase.getContactMobile(), text, fromSMS);

		} catch (Exception a) {
			logger.error("Exception " + a.getMessage());
			logger.error(GTCommon.getStackTraceAsString(a));
		}
	}

	private void sendSMSToUserforFeedback(Employee user, OrgBase ob, EntityManager em) {
		try {
			logger.info("Sending Feedback SMS to User " + user.getName());
			String text = "";
			ServiceProviderApp spApp = AdCommon.getServiceProviderAppBySP(ob.getOrg().getOrganisationId(), em);

			String spName = "FIRST Cars";

			if (spApp != null) {
				spName = spApp.getOrg().getShortName();
			}
			text = "Thank you for using " + spName
					+ ". We look forward to serving you again. Team "+ spName;
			text = text.replace("/", "%2F");

			logger.info("Message : " + text);
			String fromSMS = AdCommon.getFromSMSByBase(ob, em);
			AdCommon.insertSMSInDB(user.getMobile1(), text, fromSMS);

		} catch (Exception a) {
			// logger.error("Exception " + a.getMessage());
			logger.error(GTCommon.getStackTraceAsString(a));
		}
	}

	/*
	 * private void updateInvoiceNumber(){ try{ String
	 * ihObject="from InvoiceHeader order by invoiceId desc";
	 * logger.info("query----------"+ihObject); Query queryTogetTrip =
	 * em.createQuery(ihObject); queryTogetTrip.setMaxResults(1); ih =
	 * (InvoiceHeader) queryTogetTrip.getSingleResult();
	 * 
	 * 
	 * if(ih!=null){ if(!em.isOpen()){ em = emf.createEntityManager(); }
	 * em.getTransaction().begin(); try{ Date date=new
	 * Date(System.currentTimeMillis()); Calendar cal = Calendar.getInstance();
	 * cal.setTime(date); int year = cal.get(Calendar.YEAR); String
	 * invoicenumber=year+"/"+ih.getInvoiceId();
	 * logger.info("Invoice number---------"
	 * +invoicenumber+"   invoice header----"+ih.getInvoiceId());
	 * ih.setInvoiceNumber(invoicenumber); em.getTransaction().commit();
	 * }catch(Exception e1){ logger.info("Exception -------"+e1); }finally {
	 * if(em.isOpen()){ em.close(); } }
	 * 
	 * 
	 * } }catch(NoResultException e){
	 * logger.error("Exception for no updateInvoiceNumber request:"+e.getMessage
	 * () ); logger.error(GTCommon.getStackTraceAsString(e)); ih=null; }
	 * catch(Exception e1){ logger.error("Normal Exception:"+e1);
	 * logger.error(GTCommon.getStackTraceAsString(e1)); ih=null; } }
	 */

	private void callDirectUrl() {
		String url = props.getProperty("directUrl");
		// is not there is property file
		urlvalue = url + "/odocon/dataUpdate?Entity=Trip&tripId=" + trip.getTripId();

		try {
			URL myURL = new URL(urlvalue);
			logger.info("URL-------------" + myURL);
			HttpURLConnection myURLConnection = (HttpURLConnection) myURL.openConnection();
			myURLConnection.setRequestMethod("GET");
			int responseCode = myURLConnection.getResponseCode();
			System.out.println("url value-----> " + myURL);
			System.out.println("responseCode value----> " + responseCode);
			logger.info("responce code---" + responseCode);
			logger.info("Direct URL successfully called for TripId=" + trip.getTripId());
		} catch (MalformedURLException me) {
			logger.error("Normal Exception:" + me);
			logger.error(GTCommon.getStackTraceAsString(me));
		} catch (IOException e) {
			logger.info("Unsuccessfully calling of direct URL for tripId=" + trip.getTripId());
			logger.error("Exception for direct url " + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			// e.printStackTrace();
		}

		catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
		}
	}
	
		private void callFCURL(long trpId){
			String url=props.getProperty("directFCUrl");
			urlvalue = url+"/route/calculateDistance/"+trpId;

			try {
				URL myURL = new URL(urlvalue);
				logger.info("URL-------------"+myURL);
				HttpURLConnection myURLConnection = (HttpURLConnection) myURL.openConnection();
				myURLConnection.setRequestMethod("GET");
				int responseCode = myURLConnection.getResponseCode();
				System.out.println("FC url value-----> "+ myURL);
				System.out.println("FC responseCode value----> "+responseCode);
				logger.info("FCresponce code---"+responseCode);
				logger.info("FC URL successfully called for TripId="+trpId);
			} 
			catch(MalformedURLException me)
			{
				logger.error("Normal Exception:"+me);
				logger.error(GTCommon.getStackTraceAsString(me));
			} 
			catch (IOException e) {
				logger.info("Unsuccessfully calling of FC URL for tripId="+trip.getTripId());
				logger.error("Exception for FC url "+ e.getMessage() );
				logger.error(GTCommon.getStackTraceAsString(e));
				//e.printStackTrace();
			}

			catch(Exception e1){
				logger.error("Normal Exception:"+e1);
				logger.error(GTCommon.getStackTraceAsString(e1));
			}
		}

	// Following method is used for check driver vehicle assoc is active or not.
	public boolean checkDriverVehicleAssoc(Trip trip, String imei, EntityManager em) {
		logger.info("------Inside checkDriverVehicleAssoc()");
		boolean isActive = true;

		try {
			DriverDeviceAssoc driverDeviceAssoc = new DriverDataLogin().getDriverDeviceAssoc(imei, em);

			if (driverDeviceAssoc == null) {
				logger.info("------Trip Start ......... driverDeviceAssoc ......... null");
				logger.info("--------- Driver already released from portal........");
				return false;
			}

			long driverId = driverDeviceAssoc.getDriver().getDriverId();
			DriverVehicleAssoc driverVehicleAssoc = new DriverDataLogin().getDriverVehicleAssoc(driverId, em);
			;

			if (driverVehicleAssoc == null) {
				logger.info("------Trip Start ......... driverVehicleAssoc ......... null");
				logger.info("--------- Driver already release from portal........");
				return false;
			}

			if (driverVehicleAssoc.getVehicle().getVehicleId() != trip.getVehicle().getVehicleId()) {
				logger.info(
						"------Trip Start ......... Vehicle changed Device vehicle and trip vehicle is different.........");
				logger.info("------Trip Start ......... Device vehicle " + driverVehicleAssoc.getVehicle().getRegNo()
						+ " Trip vehicle " + trip.getVehicle().getRegNo());
				return false;
			}

			if (driverDeviceAssoc.getDriver().getDriverId() != trip.getDriver().getDriverId()) {
				logger.info(
						"------Trip Start ......... driver changed Device driver and trip driver is different.........");
				logger.info("------Trip Start ......... Device Driver " + driverDeviceAssoc.getDriver().getName() + " ("
						+ driverDeviceAssoc.getDriver().getMobile() + ")" + " Trip Driver " + trip.getDriver().getName()
						+ " (" + trip.getDriver().getMobile() + ")");
				return false;
			}

		} catch (Exception e) {
			isActive = false;
			logger.error("Normal Exception:" + e);
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		logger.info("------ Return value: " + isActive);
		return isActive;
	}
	

}

/*
 * if rate_type = 'local transfers' {
 * 
 * if totalkms < service_type.minKm { amount = service_type.base_rate /// add
 * type = 1, baseRate, amount to InvoiceItem } else { mins_over_3 = totalmins-3
 * amount = totalkms* tariff.normalFarePerKm + mins_over_3
 * *tariff.normalFarePerMin; // add type =2, usageKmRate, usageKms,
 * usageMinuteRate, usageMinutes, amount to InvoiceItem } else if rate_type =
 * 'Packages' { convert totalmins to totalhours; round totalhours to the next
 * hour - example 3 hours 20 mins will be 4 hours;
 * 
 * overageHours = totalHours-serviceType.minHr; overageKms=
 * totalkms-serviceType.minKms amount=
 * service_type.base_rate+overageHours*tariff.extraHrCost+overageKms*tariff.
 * extraKmCost; //add two invoice Item Records /// add type = 1, baseRate,
 * amount to InvoiceItem // add type =3, overageKmRate, overageKms,
 * overageHrRate, overageHours, amount to InvoiceItem
 * 
 * } else if rate_type = 'OutStation ' { /////parameterlist will contain day
 * wise - total kms
 * 
 * double amount = 0; for (everyday) {
 * 
 * if day wise - total kms >serviceType.minKms kmsToConsider = day wise - total
 * kms // else kmsToConsider = serviceType.minKms
 * 
 * 
 * dayAmount= kmsToConsider*tariff.normalFarePerKm +tariff.driverAllowance
 * amount = dayAmount+amount; // add type =2, usageKmRate=
 * tariff.normalFarePerKm, usageKms = kmsToConsider, amount = dayAmount,
 * driverAllowance to InvoiceItem
 * 
 * } else if rate_type = 'OutStation_Transfers' { amount=service_type.base_rate;
 * /// add type = 1, baseRate, amount to InvoiceItem
 * 
 * 
 * } taxAmount= amount + 5.6% service tax; totalAmount = taxAmount +
 * parkingcharges + tollcharges
 * 
 * Add all fields to InvoiceHeader table.
 * 
 * 
 * 
 * 
 * }
 * 
 * 
 * 
 */
