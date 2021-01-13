package com.adeona.ws;

import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.jboss.logging.Logger;

import com.adeona.orm.BillingRule;
import com.adeona.orm.Device;
import com.adeona.orm.Driver;
import com.adeona.orm.DriverDeviceAssoc;
import com.adeona.orm.Employee;
import com.adeona.orm.EmployeeCheckInOut;
import com.adeona.orm.Garage;
import com.adeona.orm.InvoiceHeader;
import com.adeona.orm.InvoiceItem;
import com.adeona.orm.OrgBase;
import com.adeona.orm.OrgCorporateAssoc;
import com.adeona.orm.OrgRateTypeInvoiceAssoc;
import com.adeona.orm.OutgoingBrokerEvents;
import com.adeona.orm.Tariff;
import com.adeona.orm.Trip;
import com.adeona.orm.UserFeedback;
import com.gt.util.GTCommon;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CommonQueries {

	static Logger logger = Logger.getLogger(AdCommon.class);
	static Trip trip;
	static EmployeeCheckInOut ecio;
	static Device device;
	static Tariff tariff;
	static InvoiceHeader invoiceheader;
	static boolean kmVerbiage = false;
	static boolean minVerbiage = false;
	static boolean mins_over3Verbiage = false;
	static Driver driver;
	static List<DriverDeviceAssoc> ddassocList = null;
	static Garage garage;
	static UserFeedback userfeedback;

	private static final String API_KEY = "AIzaSyAc8F3rqXGY6bNhZPLMeETwqdDyIqmBLT4";
	OkHttpClient client = new OkHttpClient();

	// getting Trip using tripId
	public static Trip getTripUsingTripId(long tripId, EntityManager em) {
		try {
			String tripObject = "From Trip where tripId=" + tripId;
			logger.info("query----------" + tripObject);
			Query queryTogetTrip = em.createQuery(tripObject);
			trip = (Trip) queryTogetTrip.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no trip corresponding to trip id in request:" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			trip = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			trip = null;
		}

		return trip;
	}

	// getting ecio using tripId
	public static EmployeeCheckInOut getEciousingTrip(long tripId, EntityManager em) {

		try {
			String querryToFindEmployeeId = "FROM EmployeeCheckInOut where tripId=" + tripId;
			Query queryTogetEmployeeId = em.createQuery(querryToFindEmployeeId);
			ecio = (EmployeeCheckInOut) queryTogetEmployeeId.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no trip corresponding to trip id in request:" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			ecio = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			ecio = null;
		}
		return ecio;
	}

	// getting device using imei
	public static Device getDeviceUsingImei(String imei, EntityManager em) {

		try {
			String querryToFindDevice = "FROM Device where serialNumber=" + imei;
			Query queryTogetEmployeeId = em.createQuery(querryToFindDevice);
			logger.info("query-------------" + querryToFindDevice);
			device = (Device) queryTogetEmployeeId.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no trip corresponding to trip id in request:" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			device = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			device = null;
		}
		return device;
	}

	public static Tariff getTariff(long tripId, EntityManager em) {
		logger.info("-----------------------get Tariff-----------------------------");

		tariff = trip.getBookingReq().getTariff();
		String baseUsingTripId = null;
		try {

			if (tariff != null) {
				if (tariff.getCorporate() != null) {
					logger.info("If corporate is not null");
					// baseUsingTripId="select * From Tariff t, Trip trip,
					// BookingRequest b, ServiceType s, VehicleType v , Employee
					// emp where b.bookingReqId=trip.bookingReqId and
					// s.serviceTypeId=b.serviceTypeId and
					// v.vehTypeId=b.vehTypeId and
					// t.serviceTypeId=s.serviceTypeId and
					// t.vehTypeId=v.vehTypeId and t.baseId=trip.baseId and
					// emp.employeeId=b.employeeId and
					// t.corporateId=emp.corporateId and trip.tripId="+tripId;
					baseUsingTripId = "select * From Tariff t, Trip trip, BookingRequest b where b.bookingReqId=trip.bookingReqId and t.tariffId=b.tariffId and t.baseId=trip.baseId and t.status=1 and trip.tripId="
							+ tripId;
				} else {
					logger.info("If corporate is Retail");
					// baseUsingTripId="select * From Tariff t, Trip trip,
					// BookingRequest b, ServiceType s, VehicleType v where
					// b.bookingReqId=trip.bookingReqId and
					// s.serviceTypeId=b.serviceTypeId and
					// v.vehTypeId=b.vehTypeId and
					// t.serviceTypeId=s.serviceTypeId and
					// t.vehTypeId=v.vehTypeId and t.baseId=trip.baseId and
					// t.corporateId is null and t.status=1 and
					// trip.tripId="+tripId;
					baseUsingTripId = "select * From Tariff t, Trip trip, BookingRequest b, ServiceType s, VehicleType v where b.bookingReqId=trip.bookingReqId and t.tariffId=b.tariffId and t.serviceTypeId=s.serviceTypeId and t.vehTypeId=v.vehTypeId and t.baseId=trip.baseId and t.corporateId is null and t.status=1 and trip.tripId="
							+ tripId;
				}
			} else {
				logger.info("No Tariff to booking Request");
			}
			Query query = em.createNativeQuery(baseUsingTripId, Tariff.class);
			logger.info("Query-----------------" + baseUsingTripId);
			tariff = (Tariff) query.getSingleResult();
			logger.info("\n\n\n Tariff :" + tariff.getTariffId());

		} catch (NoResultException e) {
			logger.info("In getTariff Catch block No Result found----------------------");
			logger.error("Exception in  getTariff :" + e.getMessage());
		} catch (Exception e) {
			logger.info("In getTariff Catch block ----------------------");
			logger.error("Exception in getTariff :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}

		return tariff;

	}

	// Following method is used for get BillingRule list on the basis of
	// corporateId, orgnisationId.
	public static List<BillingRule> getBillingRuleList(long corpId, long orgId, EntityManager em) {
		List<BillingRule> billingRuleList = null;
		logger.info("++++++++++++++++Inside getBillingRuleList");
		logger.info("get Corporate Id: ---------:" + corpId);
		logger.info("get Organization Id: ---------:" + orgId);
		try {
			String queryString = "FROM OrgCorporateAssoc where corporateId=" + corpId + " and organisationId=" + orgId;
			Query query = em.createQuery(queryString);
			logger.info("query-------------" + queryString);
			OrgCorporateAssoc objOCA = (OrgCorporateAssoc) query.getSingleResult();
			billingRuleList = objOCA.getBillingRuleList();
		} catch (NoResultException e) {
			logger.error("Exception in  getBillingRuleList :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			return billingRuleList = null;
		} catch (Exception e) {
			logger.error("Exception in getBillingRuleList :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			return billingRuleList = null;
		}
		return billingRuleList;
	}

	// Following method is used for getting OrgCorpAssoc on the basis of
	// corporateId, orgnisationId.
	public static OrgCorporateAssoc getOrgCorpAssoc(long corpId, long orgId, EntityManager em) {

		OrgCorporateAssoc objOCA = null;

		try {
			logger.info("-------------Inside getOrgCorpAssoc()");
			String queryString = "FROM OrgCorporateAssoc where corporateId=" + corpId + " and organisationId=" + orgId;
			Query query = em.createQuery(queryString);
			logger.info("query-------------" + query);
			objOCA = (OrgCorporateAssoc) query.getSingleResult();
		} catch (NoResultException e) {
			logger.error("----- No result found: " + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			objOCA = null;
		} catch (Exception e) {
			logger.error("Exception in getOrgCorpAssoc :" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			objOCA = null;
		}
		return objOCA;
	}

	// Following method is used for get the invoice generate flag value on the
	// basis of corporateId, orgnisationId.
	public static boolean getInvGenFlag(long corpId, long orgId, EntityManager em) {
		boolean flag = false;
		try {
			String queryString = "FROM OrgCorporateAssoc where corporateId=" + corpId + " and organisationId=" + orgId;
			Query query = em.createQuery(queryString);
			logger.info("query-------------" + query);
			OrgCorporateAssoc objOCA = (OrgCorporateAssoc) query.getSingleResult();
			flag = objOCA.isGenerateInvoice();
		} catch (NoResultException e) {
			logger.error("------ No result found" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
		} catch (Exception e) {
			logger.error("Exception in getInvGenFlag :" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
		}
		logger.info("Return Flag Value------" + flag);
		return flag;
	}

	// Following method is used for get the invoice generate flag from
	// OrgRateTypeInvoiceAssoc.
	public static OrgRateTypeInvoiceAssoc getInvoiceGenerateFlag(long orgId, long rateTypeId, EntityManager em) {

		OrgRateTypeInvoiceAssoc orgRTInvAsosc = null;

		try {
			String queryString = "FROM OrgRateTypeInvoiceAssoc where rateTypeId=" + rateTypeId + " and organisationId="
					+ orgId;
			Query query = em.createQuery(queryString);
			logger.info("query-------------" + query);
			orgRTInvAsosc = (OrgRateTypeInvoiceAssoc) query.getSingleResult();

		} catch (NoResultException e) {
			logger.error("Exception in  getInvoiceGenerateFlag : No result found for Rate Type Id: " + rateTypeId
					+ " and OrgId: " + orgId);
			// logger.error(GTCommon.getStackTraceAsString(e));
			return orgRTInvAsosc;
		} catch (Exception e) {
			logger.error("Exception in getInvoiceGenerateFlag :" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			return orgRTInvAsosc;
		}
		return orgRTInvAsosc;
	}

	public static double localTransfer(InvoiceItem invoiceItem, double totalKms, long totalMins,
			InvoiceItem invoiceItem2) {
		double amount = 0;
		if (totalKms < tariff.getServiceType().getMinKm()) {
			amount = tariff.getBaseRate();
			logger.info("Amount--------------" + amount);
			invoiceItem.setBaseRate(amount); // baserate=amount
			invoiceItem.setAmount(amount);
			invoiceItem.setInvoiceItemVerbiage("base rate");
			invoiceItem
					.setInvoiceRateVerbiage("Rs. " + amount + " for minimum kms " + tariff.getServiceType().getMinKm());
		} else { // minimumn mins is 3
			long mins_over_3 = 0;
			if (totalMins > 3) {
				mins_over_3 = totalMins - 3;
				logger.info("Mins over 3-----------------" + mins_over_3);
				amount = totalKms * tariff.getNormalFarePerKm() + mins_over_3 * tariff.getNormalFarePerMin();
			} else {
				amount = totalKms * tariff.getNormalFarePerKm();

			}

			logger.info("Amount --------------" + amount);
			invoiceItem.setUsageKms(totalKms);
			invoiceItem.setUsageKmRate(totalKms * tariff.getNormalFarePerKm());

			invoiceItem.setAmount(totalKms * tariff.getNormalFarePerKm());
			invoiceItem.setInvoiceRateVerbiage("Km Rate");
			invoiceItem.setInvoiceItemVerbiage(totalKms + " Km @ Rs. " + tariff.getNormalFarePerKm() + " per Km");

			if (mins_over_3 != 0) {
				mins_over3Verbiage = true;
				invoiceItem2.setUsageMinutes(totalMins);
				invoiceItem2.setUsageMinuteRate(mins_over_3 * tariff.getNormalFarePerMin());
				invoiceItem2.setAmount(mins_over_3 * tariff.getNormalFarePerMin());
				invoiceItem2.setInvoiceRateVerbiage("Minute Rate");
				invoiceItem2.setInvoiceItemVerbiage(
						mins_over_3 + " Min @ Rs. " + tariff.getNormalFarePerMin() + " per Min");
			}
		}
		return amount;
	}

	public static double packages(InvoiceItem invoiceItem, double totalKms, long totalMins, InvoiceItem overageKmsItem,
			InvoiceItem overageMinItem, InvoiceItem driverVerbiageInvoiceItem, Tariff tariff) {
		double amount = 0;
		long hrs = totalMins / 60;
		long mins = totalMins % 60;
		long totalHours;

		if (mins != 0) {
			totalHours = hrs + 1;
		} else
			totalHours = hrs;
		logger.info("Total hours----------------" + totalHours);
		logger.info("Total kms-----------------" + totalKms);

		double minHrs = tariff.getServiceType().getMinHr();
		long minKms = tariff.getServiceType().getMinKm();
		logger.info("Min km------------------" + minKms + "  min hrs---------------------" + minHrs);
		int overageHrs = 0;
		int overageHrsRate = 0;
		int overageKms = 0;
		int overageKmsRate = 0;
		if (totalHours > minHrs && totalKms < minKms) {
			overageHrs = (int) (totalHours - tariff.getServiceType().getMinHr());
			overageHrsRate = (int) (overageHrs * tariff.getExtraHrCost());

			amount = tariff.getBaseRate() + overageHrsRate;
			logger.info("Amount if totalhrs are more than minimun hrs-----------" + amount);
		} else if (totalHours < minHrs && totalKms > minKms) {
			overageKms = (int) (totalKms - tariff.getServiceType().getMinKm());
			overageKmsRate = (int) (overageKms * tariff.getExtraKmCost());

			amount = tariff.getBaseRate() + overageKmsRate;
			logger.info("Amount if totalKms are more than mininum kms----------------" + amount);
		} else if (totalHours > minHrs && totalKms > minKms) {
			overageHrs = (int) (totalHours - tariff.getServiceType().getMinHr());
			overageHrsRate = (int) (overageHrs * tariff.getExtraHrCost());
			overageKms = (int) (totalKms - tariff.getServiceType().getMinKm());
			overageKmsRate = (int) (overageKms * tariff.getExtraKmCost());

			amount = tariff.getBaseRate() + overageHrsRate + overageKmsRate;
			logger.info("Amount if both exceeds---------------" + amount);
		} else {
			amount = tariff.getBaseRate();
			logger.info("Amount--------------" + amount);
		}

		// add type = 1, baseRate, amount to InvoiceItem
		invoiceItem.setType(1);
		invoiceItem.setBaseRate(tariff.getBaseRate());
		invoiceItem.setAmount(amount);
		invoiceItem.setInvoiceItemVerbiage("base rate for");
		invoiceItem.setInvoiceRateVerbiage("min kms " + minKms + " and min hrs " + minHrs);

		// add type =3, overageKmRate, overageKms, overageHrRate, overageHours,
		// amount to InvoiceItem
		if (totalKms > minKms) {
			kmVerbiage = true;
			overageKmsItem.setType(3);
			overageKmsItem.setOverageKms(overageKms);
			overageKmsItem.setOverageKmRate(overageKmsRate);
			overageKmsItem.setOverageHours(overageHrs);
			overageKmsItem.setOverageHrRate(overageHrsRate);
			overageKmsItem.setAmount(overageKmsRate);
			overageKmsItem.setInvoiceRateVerbiage("Overage Km Rate");
			overageKmsItem.setInvoiceItemVerbiage(overageKms + " Km @ Rs. " + tariff.getExtraKmCost() + " per Km");
		}

		if (totalHours > minHrs) {
			minVerbiage = true;
			overageMinItem.setType(3);
			overageMinItem.setOverageKms(overageKms);
			overageMinItem.setOverageKmRate(overageKmsRate);
			overageMinItem.setOverageHours(overageHrs);
			overageMinItem.setOverageHrRate(overageHrsRate);
			overageMinItem.setAmount(overageHrsRate);
			overageMinItem.setInvoiceRateVerbiage("Overage Min Rate");
			overageMinItem.setInvoiceItemVerbiage(overageHrs + " Min @ Rs. " + tariff.getExtraHrCost() + " per Min");
		}

		// Add the driver Allowance.
		if (tariff.getDriverAllowance() != 0) {

			double totalDriverAllowance = tariff.getDriverAllowance();
			driverVerbiageInvoiceItem.setType(2);
			driverVerbiageInvoiceItem.setUsageKmRate(tariff.getNormalFarePerKm());
			driverVerbiageInvoiceItem.setUsageKms(totalKms);
			driverVerbiageInvoiceItem.setAmount(totalDriverAllowance);
			driverVerbiageInvoiceItem.setDriverAllowance(tariff.getDriverAllowance());
			driverVerbiageInvoiceItem.setInvoiceRateVerbiage("Rs. " + tariff.getDriverAllowance());
			driverVerbiageInvoiceItem.setInvoiceItemVerbiage("Driver Allowance");

			amount = amount + totalDriverAllowance;
			logger.info("Driver Allowance Amount--------------" + totalDriverAllowance);

		}

		return amount;

	}

	public static double outstation(InvoiceItem invoiceItem, InvoiceItem driverVerbiageInvoiceItem, long tripId,
			double totalKms, long noOfDays, EntityManager em) {
		double amount = 0;
		Tariff tariff = getTariff(tripId, em);
		double kmsToConsider = 0, itemVerbiage = 0, usageKms = 0, totalDriverAllownce = 0;
		for (int i = 1; i <= noOfDays; i++) {

			double dayWiseKm = totalKms / noOfDays;
			logger.info("--------------------------------------per day km--------------" + dayWiseKm);
			double minKm = tariff.getServiceType().getMinKm();

			logger.info("--------------------------------------per day min km----------" + minKm);

			if (dayWiseKm > minKm) {
				kmsToConsider = dayWiseKm;
			} else {
				kmsToConsider = minKm;
			}

			usageKms = usageKms + kmsToConsider;

			double dayAmount = kmsToConsider * tariff.getNormalFarePerKm();

			amount = dayAmount + amount;
		}

		itemVerbiage = tariff.getNormalFarePerKm();
		invoiceItem.setType(2);
		invoiceItem.setUsageKmRate(tariff.getNormalFarePerKm());
		invoiceItem.setUsageKms(usageKms);
		invoiceItem.setAmount(amount);
		invoiceItem.setDriverAllowance(tariff.getDriverAllowance());
		invoiceItem.setInvoiceRateVerbiage("Rental for " + tariff.getServiceType().getServiceTypeName());
		invoiceItem.setInvoiceItemVerbiage(usageKms + " Km @ Rs. " + itemVerbiage + " per Km");

		// for driver allowance verbiage
		if (tariff.getDriverAllowance() != 0) {
			// driverVerbiageInvoiceItem=new InvoiceItem();

			totalDriverAllownce = noOfDays * tariff.getDriverAllowance();
			driverVerbiageInvoiceItem.setType(2);
			driverVerbiageInvoiceItem.setUsageKmRate(tariff.getNormalFarePerKm());
			driverVerbiageInvoiceItem.setUsageKms(kmsToConsider);
			driverVerbiageInvoiceItem.setAmount(totalDriverAllownce);
			driverVerbiageInvoiceItem.setDriverAllowance(tariff.getDriverAllowance());
			driverVerbiageInvoiceItem.setInvoiceRateVerbiage("Driver Allowance");
			driverVerbiageInvoiceItem
					.setInvoiceItemVerbiage(noOfDays + " days @ Rs. " + tariff.getDriverAllowance() + " per day");

			amount = amount + totalDriverAllownce;

			logger.info("Driver Allowance Amount--------------" + totalDriverAllownce);

		}
		return amount;
	}

	public static double outstationTransfer(InvoiceItem invoiceItem, InvoiceItem driverVerbiageInvoiceItem,
			Tariff tariff) {
		double amount = 0;
		amount = tariff.getBaseRate();
		logger.info("Amount--------------" + amount);

		invoiceItem.setAmount(amount);
		invoiceItem.setType(4);
		invoiceItem.setBaseRate(amount); // baserate=amount
		invoiceItem.setInvoiceItemVerbiage("Rate");
		invoiceItem.setInvoiceRateVerbiage(amount + "");

		// Add the driver Allowance.
		if (tariff.getDriverAllowance() != 0) {

			double totalDriverAllowance = tariff.getDriverAllowance();
			driverVerbiageInvoiceItem.setType(2);
			driverVerbiageInvoiceItem.setUsageKmRate(tariff.getNormalFarePerKm());
			driverVerbiageInvoiceItem.setUsageKms(0);
			driverVerbiageInvoiceItem.setAmount(totalDriverAllowance);
			driverVerbiageInvoiceItem.setDriverAllowance(tariff.getDriverAllowance());
			driverVerbiageInvoiceItem.setInvoiceRateVerbiage("Rs. " + tariff.getDriverAllowance());
			driverVerbiageInvoiceItem.setInvoiceItemVerbiage("Driver Allowance");

			amount = amount + totalDriverAllowance;

			logger.info("Driver Allowance Amount--------------" + totalDriverAllowance);

		}

		return amount;
	}

	public static InvoiceHeader getInvoiceHeaderUsingTripId(long tripId, EntityManager em) {
		try {
			String ihObject = "From InvoiceHeader where tripId=" + tripId;
			logger.info("query----------" + ihObject);
			Query queryTogetInvoiceHeader = em.createQuery(ihObject);
			invoiceheader = (InvoiceHeader) queryTogetInvoiceHeader.getSingleResult();
		} catch (NoResultException e) {
			logger.error("Exception for no InvoiceHeader corresponding to trip id in request:" + e.getMessage());
			// logger.error(GTCommon.getStackTraceAsString(e));
			return invoiceheader = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			return invoiceheader = null;
		}
		logger.info("invoice header----------" + invoiceheader.getTotalAmount() + "   " + invoiceheader.getInvoiceId());
		return invoiceheader;
	}

	public static OutgoingBrokerEvents getOBEForOrg(long organisationId, String event, EntityManager em) {

		OutgoingBrokerEvents obe = null;

		try {

			String queryStr = "FROM OutgoingBrokerEvents where org.organisationId = " + organisationId
					+ " AND event = '" + event + "'";

			Object queryResult = getHQLQuerySingleResult(queryStr, em);

			if (queryResult != null) {
				obe = (OutgoingBrokerEvents) queryResult;
			}

		} catch (NoResultException ne) {
			logger.info("-------- No Records found ---------");
		}

		return obe;

	}

	public static Object getHQLQuerySingleResult(String queryStr, EntityManager em) {

		Object queryResult = null;
		Query query = em.createQuery(queryStr);
		query.setMaxResults(1);
		queryResult = query.getSingleResult();
		return queryResult;

	}

	public static boolean isBWSCallValidIfLTBooking(Trip trip, EntityManager em) {

		String ratetypeName = trip.getBookingReq().getSerType().getRateType().getRateTypeName();
		long organisationId = trip.getBase().getOrg().getOrganisationId();
		boolean callBws = true;

		if ((ratetypeName.trim().equals(WSConstants.LOCALTRANSFER))) {

			OutgoingBrokerEvents obe = getOBEForOrg(organisationId, WSConstants.OBE_EVENT_LTBOOKING, em);

			if (obe == null) {
				callBws = false;
				logger.info("----- Rate type is local transfers and LTBooking event is not registered for orgID : "
						+ organisationId + " --------------");
			}

		}

		return callBws;

	}

	public static String getPrimaryGSTN(long orgId, EntityManager em) {
		List<OrgBase> baseList = null;

		try {
			String baseQuery = "From OrgBase where organisationId = " + orgId;
			Query query = em.createQuery(baseQuery);
			baseList = (List<OrgBase>) query.getResultList();
			if ((baseList != null)) {
				Iterator<OrgBase> itr = baseList.iterator();
				while (itr.hasNext()) {
					OrgBase base = (OrgBase) itr.next();
					if (base.isPrimary()) {
						return base.getGSTNumber();
					}
				}
			}
		} catch (NoResultException e) {
			logger.info("No bases found");

		} catch (Exception e) {
			logger.error("Exception in getPrimaryGSTN :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		return "";

	}

	// getting driver using mobile number
	public static Driver getDriverUsingMobilNumber(String mobileNumber, EntityManager em) {
		try {
			String driverUsingMobile = "From Driver where mobile=" + mobileNumber + " and status='A'";
			Query query = em.createQuery(driverUsingMobile);
			driver = (Driver) query.getSingleResult();
			logger.info("Driver :" + driver.getDriverId());

		} catch (NoResultException e) {
			logger.info("Driver not found for mobile No.: " + mobileNumber);
			// logger.error(GTCommon.getStackTraceAsString(e));
		} catch (Exception e) {
			logger.error("Exception in get Driver Using MobilNumber :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		return driver;
	}

	// getting deviceId using driverId
	public static long getDeviceIdUsingDriverId(long driverId, EntityManager em) {
		long deviceId = 0;
		try {
			String driverDeviceAssocUsingDriverId = "select * from DriverDeviceAssoc dd where dd.driverId=" + driverId;
			Query query = em.createNativeQuery(driverDeviceAssocUsingDriverId, DriverDeviceAssoc.class);
			ddassocList = query.getResultList();
			if (ddassocList.size() != 0)
				deviceId = ddassocList.get(0).getDevice().getDeviceId();
			else
				return -1;
			logger.info("Device Id :" + deviceId);

		} catch (NoResultException e) {
			logger.info("Device Id not found for mobile No.: " + driverId);
		} catch (Exception e) {
			logger.error("Exception in get DeviceId Using DriverId :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
		return deviceId;
	}

	// getting Imei using deviceId
	public static String getImeiUsingDeviceId(long deviceId, EntityManager em) {
		String imeiNo = "";
		try {
			String getImeiUsingDeviceId = "select * from Device d where d.deviceId=" + deviceId;
			Query query = em.createNativeQuery(getImeiUsingDeviceId, Device.class);
			device = (Device) query.getSingleResult();
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

	// getting Trip using driverId
	public static List<Trip> getTripUsingDriverId(long driverId, EntityManager em) {
		List<Trip> tripList = null;
		try {
			String baseQuery = "From Trip where driverId=" + driverId;
			logger.info("query----------" + baseQuery);
			Query query = em.createQuery(baseQuery);
			// baseList = (List<OrgBase>) tripQuery.getResultList();
			tripList = (List<Trip>) query.getResultList();
		} catch (NoResultException e) {
			logger.error("Exception for no trip corresponding to driver id in request:" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			tripList = null;
		} catch (Exception e1) {
			logger.error("Normal Exception:" + e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			tripList = null;
		}
		return tripList;
	}

	// return tripCurrentStatus
	public static int getTripCurrentStatus(long tripId, EntityManager em) {
		int trpCurrentStatus = 0;
		try {
			String getTripCurrentStatusUsingTripId = "select * from trip t where t.tripId=" + tripId;
			Query query = em.createNativeQuery(getTripCurrentStatusUsingTripId, Trip.class);
			trip = (Trip) query.getSingleResult();
			trpCurrentStatus = trip.getTripCurrentStatus();
			logger.info("tripCurrentStatus :" + trpCurrentStatus);
		} catch (NoResultException e) {
			logger.info("tripCurrentStatus not found for Trip Id : " + tripId);
			trpCurrentStatus = 0;
		} catch (Exception e) {
			logger.error("Exception in get tripCurrentStatus Using TripId :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			trpCurrentStatus = 0;
		}
		return trpCurrentStatus;
	}

	// return garage lat lng
	public static String getLatLngFromGarage(long garageId, EntityManager em) {
		String latLng;
		try {
			String getLatLngUsingGarageId = "select * from garage where garageId=" + garageId;
			Query query = em.createNativeQuery(getLatLngUsingGarageId, Garage.class);
			garage = (Garage) query.getSingleResult();
			latLng = garage.getLatitude() + " " + garage.getLongitude();
			logger.info("latLng :" + latLng);
		} catch (NoResultException e) {
			logger.info("LatLong for Garage Id : " + garageId);
			latLng = "";
		} catch (Exception e) {
			logger.error("Exception in getting LatLong for Garage Id : :" + e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
			latLng = "";
		}
		return latLng;
	}
	

}
