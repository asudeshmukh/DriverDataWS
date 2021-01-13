package com.adeona.ws;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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

import com.adeona.orm.BillingRule;
import com.adeona.orm.BookingRequest;
import com.adeona.orm.Corporate;
import com.adeona.orm.Employee;
import com.adeona.orm.EmployeeCheckInOut;
import com.adeona.orm.InvoiceHeader;
import com.adeona.orm.InvoiceItem;
import com.adeona.orm.OrgBase;
import com.adeona.orm.OrgCorporateAssoc;
import com.adeona.orm.OrgRateTypeInvoiceAssoc;
import com.adeona.orm.Organisation;
import com.adeona.orm.Tariff;
import com.adeona.orm.Trip;
import com.gt.util.GTCommon;

@Path("/Invoice")
public class Invoice {
	private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("odocon");

	//EntityManager emGlobal;
	private static Logger logger = Logger.getLogger(Invoice.class);
	static String urlvalue = null;
	static Properties props;
	Trip trip;
	EmployeeCheckInOut ecio;
	InvoiceHeader invoiceheader;
	InvoiceHeader invoiceHeader;
	Tariff tariff;
	ArrayList<InvoiceItem> invoiceitems;
	String strDecimalFormat = "###0.00";
	double otherAlwnc = 0.0;
	private static String brokerwsURL = null;
	private static String brokerwsUserName = null;
	private static String brokerwsPassword = null;
	
	public Invoice() {
		logger.info("*********************Invoice***********************************");
		//emGlobal = emf.createEntityManager();

	}

	static{
		props = AdCommon.getPropFromDriverAppWS();
		if (props != null) {
			urlvalue = props.getProperty("server");
		}
	}



	private InvoiceHeader getInvoiceHeaderUsingTripId(long tripId, EntityManager em){
		try{
			String ihObject="From InvoiceHeader where tripId="+tripId;
			logger.info("query----------"+ihObject);
			Query queryTogetInvoiceHeader = em.createQuery(ihObject);
			invoiceheader = (InvoiceHeader) queryTogetInvoiceHeader.getSingleResult();
		}catch(NoResultException e){
			logger.error("Exception for no InvoiceHeader corresponding to trip id in request:"+e.getMessage() );
			logger.error(GTCommon.getStackTraceAsString(e));
			invoiceheader=null;
			return invoiceheader;
		}
		catch(Exception e1){
			logger.error("Normal Exception:"+e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			invoiceheader=null;
			return invoiceheader;
		}
		logger.info("invoice header----------"+invoiceheader.getTotalAmount()+"   "+invoiceheader.getInvoiceId());
		return invoiceheader;
	}

	private ArrayList<InvoiceItem> getInvoiceItemUsingInvoiceId(long invoiceId, EntityManager em){
		try{
			String ihObject="From InvoiceItem where invoiceId="+invoiceId;
			logger.info("query----------"+ihObject);
			Query queryTogetInvoiceItem = em.createQuery(ihObject);
			invoiceitems= (ArrayList<InvoiceItem>) queryTogetInvoiceItem.getResultList();
		}/*catch(NoResultException e){
			logger.error("Exception for no InvoiceHeader corresponding to trip id in request:"+e.getMessage() );
			logger.error(GTCommon.getStackTraceAsString(e));
			invoiceitems=null;
		}*/
		catch(Exception e1){
			logger.error("Normal Exception:"+e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
			invoiceitems=null;
		}
		return invoiceitems;
	}



	@GET
	@Path("/UpdateInvoice")	
	@Consumes({"multipart/related,text/plain"})
	public String updateInvoice(@QueryParam("tripId")long tripId,@QueryParam("groKm")double groKm,@QueryParam("grcKm")double grcKm,
			@QueryParam("gsoKm")double gsoKm,@QueryParam("gscKm")double gscKm,@QueryParam("otherAllowance")double otherAllowance, @QueryParam("sendInvoice")String sendInvoice){
		
		logger.info("-----------------------UpdateInvoice------------------------------ tripId: "+tripId +" groKm : "+groKm +" grcKm "+grcKm +" gsoKm : "+gsoKm
				+" gscKm : "+gscKm +" otherAllowance : "+otherAllowance +" sendInvoice : "+sendInvoice);
		
		EntityManager em = emf.createEntityManager();
		EntityTransaction et = null;
		String ret=null, invGenerateFor = "", rateTypeName = "", invoiceNumber = "";
		long noOfDays = 0;
		double totalKm = 0, tollAmount = 0, parkingAmount = 0;
		boolean isInvGenerate = true;
		Date fromDate = null, toDate=null;
		long totalMins = 0;
		int billingRuleType = 0;
		BillingRule billingRuleList = null;
		int sendRecalInvoice = -1; // means parameter absent, in that case, will use logic mentioned in bug 2185 -MB
		otherAlwnc = otherAllowance;

		if (sendInvoice != null) {
			if (sendInvoice.equals("true")) {
				sendRecalInvoice = 1; //send invoice 
			} else {
				sendRecalInvoice = 0;
			}
		}

		try {
			logger.info("------Invoice Regenerate---------------------");
			trip=CommonQueries.getTripUsingTripId(tripId,em);
			ecio=CommonQueries.getEciousingTrip(tripId,em);
			Employee emp=ecio.getEmployee();
			tariff=CommonQueries.getTariff(tripId,em);
			BookingRequest br = trip.getBookingReq();

			OrgRateTypeInvoiceAssoc orgRTInvAsosc = CommonQueries.getInvoiceGenerateFlag(tariff.getBase().getOrg().getOrganisationId(), tariff.getServiceType().getRateType().getRateTypeId(), em);
			if(orgRTInvAsosc != null){
				isInvGenerate = orgRTInvAsosc.isGenerateInvoice();
			}

			if(isInvGenerate){
				
				et = em.getTransaction();
				et.begin();
				//Check invoice Header is already present or not.

				invoiceheader=getInvoiceHeaderUsingTripId(tripId, em);

				if(invoiceheader != null){

					invoiceNumber = invoiceheader.getInvoiceNumber();

					ArrayList<InvoiceItem> invItemList = getInvoiceItemUsingInvoiceId(invoiceheader.getInvoiceId(), em);

					if((invItemList != null) && (! invItemList.isEmpty())){

						for(InvoiceItem invItem : invItemList){

							//et.begin();

							em.remove(invItem);

							//et.commit();

						}

					}

					//et.begin();

					em.remove(invoiceheader);

					//et.commit();

				}

				rateTypeName=tariff.getServiceType().getRateType().getRateTypeName();

				noOfDays = getNoOfDays(ecio.getCheckin(), ecio.getCheckout());

				if(noOfDays == 0){
					noOfDays = 1;
					logger.info("++++++++++++++No. of Days: "+noOfDays);
				}
				else{
					logger.info("++++++++++++++No. of Days: "+noOfDays);
				}

				totalKm = gscKm - gsoKm;// Common GSO to GSC
				tollAmount = trip.getTollAmount();
				parkingAmount = trip.getParkingAmount();

				if(emp.getCorporate()!=null && tariff.getCorporate()!=null){

					OrgCorporateAssoc ocAssoc = CommonQueries.getOrgCorpAssoc(tariff.getCorporate().getCorporateId(), tariff.getBase().getOrg().getOrganisationId(), em);

					List<BillingRule> rulelist = CommonQueries.getBillingRuleList(tariff.getCorporate().getCorporateId(), tariff.getBase().getOrg().getOrganisationId(),em);
					logger.info("\n\n\n \t\t\t billing rules size--------:"+rulelist.size());
					if(rulelist.size()!=0){

						for(BillingRule billingrule:rulelist){
							if(billingrule.getRateType().getRateTypeName().equals(br.getSerType().getRateType().getRateTypeName())){
								billingRuleList = billingrule;
								logger.info("\n\n\n \t\t\t billing rule type--------:"+billingRuleList.getRuleType());

								logger.info("\n\n\n \t\t\t billing rule for invoice------rule Id is:"+billingRuleList.getRuleId());
								billingRuleType = billingrule.getRuleType();
								if(billingrule.getRuleType()==1){
									totalKm = grcKm - groKm;//GRO to GRC
									fromDate=trip.getGRO();
									toDate=trip.getGRC();
								}
								else if(billingrule.getRuleType()==3){
									totalKm = grcKm - gsoKm;//GSO to GRC
									fromDate=ecio.getCheckin();
									toDate=trip.getGRC();
								}
								else if(billingrule.getRuleType()==4){
									totalKm = gscKm - groKm;//GRO to GSC
									fromDate=trip.getGRO();
									toDate=ecio.getCheckout();
								}
							}
						}
					}
					invGenerateFor = "corporate";
				}
				else if(emp.getCorporate()==null || tariff.getCorporate()==null){
					invGenerateFor = "retail";
					fromDate=ecio.getCheckin();
					toDate=ecio.getCheckout();
				}
				//Calculate Total Minutes.
				if(fromDate!=null && toDate!=null){
					totalMins=calculateTimeDifference(fromDate, toDate);
					//noOfDays = getNoOfDays(fromDate, toDate);
				}else{
					totalMins=calculateTimeDifference(ecio.getCheckin(), ecio.getCheckout());
					//noOfDays = getNoOfDays(ecio.getCheckin(), ecio.getCheckout());
				}
				logger.info("---Total Kms: "+totalKm+" Total Minutes: "+totalMins);

				String res = createInvoice(totalKm, totalMins, trip, billingRuleList, tollAmount, parkingAmount, rateTypeName, noOfDays, invoiceNumber, tariff, invGenerateFor, em);

				if(! "Error".equalsIgnoreCase(res)){

					Trip addedTrip = null;
					EmployeeCheckInOut addedEcio = null;

					if(trip!=null && ecio!=null){
						//et.begin();
						trip.setGroKm(groKm);
						trip.setGrcKm(grcKm);
						ecio.setCheckinKm(gsoKm);
						ecio.setCheckoutKm(gscKm);
						addedTrip = em.merge(trip);
						addedEcio = em.merge(ecio);
						ret= "Success";

						//sendSMSToUserOnPaymentType(ecio.getEmployee(), trip.getBase(), Double.parseDouble(totalAmt));
					}else{
						ret= "Error";
					}
					logger.info("----------- Before commit");
					et.commit();
					logger.info("----------- after commit");
					if ("Success".equalsIgnoreCase(ret)) {
						
						AdCommon.generateInvoiceReport(tripId, addedTrip, addedEcio, invGenerateFor,br.getPaymentOption(),billingRuleType,sendRecalInvoice, em);
					}
				}
			}
			else{
				ret = "Success";
			}
			logger.info("--------- Calling broker web service --------------");
			
			if ((brokerwsURL == null) || (brokerwsUserName == null) || (brokerwsPassword == null)) {

				Properties props = AdCommon.getPropFromDriverAppWS();
				brokerwsURL = props.getProperty("brokerwsURL");
				brokerwsUserName = props.getProperty("brokerwsUserName");
				brokerwsPassword = props.getProperty("brokerwsPassword");

			}

			if (CommonQueries.isBWSCallValidIfLTBooking(trip, em)) {
				AdCommon.callBrokerWebService(trip, WSConstants.OBE_EVENT_INVOICE, brokerwsURL, brokerwsUserName, brokerwsPassword, em);
			}
			
		} catch (Exception e) {
			ret= "Error, Please try again";
			logger.error("Exception in  updateInvoice :"+ e.getMessage());
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

	//Following method is used to call the invoice generation methods for service type Local Transfer, Package, Outstation, Outstation Transfer.
	public String createInvoice(double totalKm, long totalMin, Trip trip, BillingRule billingRule, double tollAmount, double parkingAmount, String rateTypeName, long noOfDays, String invoiceNumber, Tariff tariff, String invGenerateFor, EntityManager em){

		String response = "";
		List<InvoiceHeader> invoiceHeaderList = getInvoiceHeadersUsingTripId(trip.getTripId(), em);
		
		if (invoiceHeaderList != null) {
			if (invoiceHeaderList.size() > 0) {
				logger.info("WARNING ******************************************** invoice header more than one entry found for trip Id "+trip.getTripId()
						+"\n this can happen if biling rule changed after GSC");
				
			}
			for (Iterator iterator = invoiceHeaderList.iterator(); iterator.hasNext();) {
				InvoiceHeader invoiceHeader = (InvoiceHeader) iterator.next();
				if (invoiceHeader != null) {
					invoiceNumber = invoiceHeader.getInvoiceNumber();

					ArrayList<InvoiceItem> invItemList = getInvoiceItemUsingInvoiceId(invoiceHeader.getInvoiceId(), em);

					if((invItemList != null) && (! invItemList.isEmpty())){

						for(InvoiceItem invItem : invItemList){
							em.remove(invItem);

						}

					}

					//et.begin();
					logger.info("deleted invoice header .......................... "+invoiceHeader.getInvoiceId());
					em.remove(invoiceHeader);
				}

			}
			
			
		}
		
		try{

			if(rateTypeName.equals(WSConstants.PACKAGES)){
				response = createInvoiceForPackage(trip,billingRule,totalMin,totalKm,tariff, tollAmount, parkingAmount,  invoiceNumber, invGenerateFor, em);
			}
			else if(rateTypeName.equals(WSConstants.LOCALTRANSFER)){
				response = createInvoiceForLocalTransfer(trip,billingRule,totalMin,totalKm,tariff, tollAmount, parkingAmount,  invoiceNumber, invGenerateFor, em);
			}
			else if(rateTypeName.equals(WSConstants.OUTSTATION)){
				response = createInvoiceForOutstation(trip, billingRule, totalMin, totalKm, tariff, tollAmount, parkingAmount,  invoiceNumber, invGenerateFor, noOfDays, em);
			}
			else if(rateTypeName.equals(WSConstants.OUTSTATION_TRANSFER)){
				response = createInvoiceForOutstationTransfer(trip,billingRule,totalMin,totalKm,tariff, tollAmount, parkingAmount,  invoiceNumber, invGenerateFor, em);
			}
			else if(rateTypeName.equals(WSConstants.HOURLY_PACKAGES)){
				response = createInvoiceForHourlyPackages(trip,billingRule,totalMin,totalKm,tariff, tollAmount, parkingAmount,  invoiceNumber, invGenerateFor, em);
			}

			
		}
		catch(Exception e){
			logger.error("*****Exception in  Create Invoice :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));

			response = "Error";
		}
		return response;
	}



	//Following method is used for PACKAGE calculation.
	private String createInvoiceForPackage(Trip trip, BillingRule billingRule, long totalMins, double totalKms, Tariff tariff, double tollAmt, double parkingAmt,String invoiceNumber, String invGenerateFor, EntityManager em){

		String response = null, stateName = "";
		double amount = 0.0, actualKms = 0.0, billableKms = 0.0, finalKms = 0.0, extraKms = 0.0, grandTotal = 0.0;
		double extraKmAmount = 0.0, extraHrsAmount = 0.0, baseAmount = 0.0, flatAmount = 0.0;
		double preTaxAmount = 0.0, taxAmount = 0.0, totalAmount = 0.0;
		double igstAmt = 0.0, cgstAmt = 0.0, sgstAmt = 0.0;
		double actualHrs = 0.0, billableHrs = 0.0, finalHrs = 0.0, extraHrs = 0.0;

		//EntityTransaction et = em.getTransaction();

		invoiceHeader = new InvoiceHeader();
		InvoiceItem invoiceItem = new InvoiceItem();
		InvoiceItem overageKmsItem = null;
		InvoiceItem overageMinItem = null;
		InvoiceItem driverVerbiageInvoiceItem = null;
		InvoiceItem flatRateInvoiceItem = null;

		JSONObject jsonObj = new JSONObject();

		try{
			long hrs = totalMins/60;
			long mins=totalMins%60;

			actualHrs = ((double)totalMins)/60.0;
			/*if (mins != 0) {
				actualHrs=hrs+1;
			} else {
				actualHrs=hrs;
			}
			 */
			actualKms = totalKms;

			//logger.info("\n\n\n \t\t\t billing rule type--------:"+billingRule.getRuleType());

			//logger.info("\n\n\n \t\t\t billing rule for invoice------rule Id is:"+billingRule.getRuleId());

			logger.info("----------tariff Min Kms: "+tariff.getServiceType().getMinKm()+" Min Hrs: "+tariff.getServiceType().getMinHr());

			if("CORPORATE".equalsIgnoreCase(invGenerateFor)){

				if (billingRule != null) {

					logger.info("\n\n\n \t\t\t billing rule type--------:"+billingRule.getRuleType());

					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Id is:"+billingRule.getRuleId());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule flat amount is:"+billingRule.getFlatRate());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Added Km is:"+billingRule.getAddedKm());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Added Hrs is:"+billingRule.getAddedHour());

					billableKms = actualKms + billingRule.getAddedKm();
					billableHrs = actualHrs + billingRule.getAddedHour();
					logger.info("-------Billable Km: "+billableKms+" Billable Hrs: "+billableHrs);

					if (billingRule.getFlatRate() > 0.0){ // its flate rate calc, no need to consider extra kms and extra hrs.
						flatAmount = billingRule.getFlatRate();
					} else {

						if(tariff.getServiceType().getMinKm() < billableKms){
							finalKms = billableKms - tariff.getServiceType().getMinKm();
						} else {
							// ignore it, no extra kms
						}


						if(tariff.getServiceType().getMinHr() < billableHrs){
							finalHrs = billableHrs - tariff.getServiceType().getMinHr();
						} else {
							// ignore it, no extra hrs
						}
					}
				}
				else {
					logger.info("---------Billing Rule not found.");
					billableKms = actualKms;
					billableHrs = actualHrs;

					if(tariff.getServiceType().getMinKm() < billableKms){
						finalKms = billableKms - tariff.getServiceType().getMinKm();
					} else {
						// ignore it, no extra kms
					}


					if(tariff.getServiceType().getMinHr() < billableHrs){
						finalHrs = billableHrs - tariff.getServiceType().getMinHr();
					} else {
						// ignore it, no extra hrs
					}
				}

				if (finalKms >  0.0) {

					extraKms = finalKms;
					extraKmAmount = extraKms * tariff.getExtraKmCost();

				}


				if (finalHrs > 0.0) {

					extraHrs = finalHrs;

					double afterDecimalValue = extraHrs - Math.floor(extraHrs);

					if(afterDecimalValue > 0.0){
						if(afterDecimalValue <= 0.30){
							extraHrs = ((int)extraHrs);
						}
						else{
							extraHrs = ((int)extraHrs) + 1;
						}
					}
					extraHrsAmount = extraHrs * tariff.getExtraHrCost();

				}
			}
			else {

				billableKms = actualKms;
				billableHrs = actualHrs;

				if(tariff.getServiceType().getMinKm() < billableKms){
					finalKms = billableKms - tariff.getServiceType().getMinKm();
				} else {
					// ignore it, no extra kms
				}

				if (finalKms > 0.0){

					extraKms = finalKms;
					extraKmAmount = extraKms * tariff.getExtraKmCost();
				}


				if(tariff.getServiceType().getMinHr() < billableHrs){
					finalHrs = billableHrs - tariff.getServiceType().getMinHr();
				} else {
					// ignore it, no extra hrs
				}

				if (finalHrs > 0.0){
					extraHrs = finalHrs;

					double afterDecimalValue = extraHrs - Math.floor(extraHrs);

					if(afterDecimalValue > 0.0){
						if(afterDecimalValue <= 0.30){
							extraHrs = ((int)extraHrs);
						}
						else{
							extraHrs = ((int)extraHrs) + 1;
						}
					}
					extraHrsAmount = extraHrs * tariff.getExtraHrCost();
				}

			}
			logger.info("-------Final Km: "+finalKms+" finalHrs: "+finalHrs);
			logger.info("-------Extra Km: "+extraKms+" ExtraHrs: "+extraHrs);
			logger.info("-------Extra Km amount: "+extraKmAmount+" Extra Hrs amount: "+extraHrsAmount +" Flat Amt: "+flatAmount);

			baseAmount = tariff.getBaseRate();

			amount = baseAmount + extraKmAmount + extraHrsAmount + flatAmount;
			logger.info("-------Driver Allowance: "+tariff.getDriverAllowance());

			preTaxAmount = amount + tollAmt + parkingAmt + tariff.getDriverAllowance() + otherAlwnc;
			logger.info("-------Pre tax amount: "+preTaxAmount);
			
			double tax = calculateGST(preTaxAmount, em,trip);
			totalAmount = preTaxAmount + tax;
			totalAmount = Math.round(totalAmount);
			logger.info("+++++++++++Total amount: "+totalAmount);

			//Fill the InvoiceHeader.
			//et.begin();

			invoiceHeader.setInvoiceDate(new Date(System.currentTimeMillis()));
			//invoiceHeader.setServiceTaxAmount(taxAmount);
			//invoiceHeader.setSbCessTaxAmount(sbCessTaxAmount);
			//invoiceHeader.setKkCessTaxAmount(kkCessTaxAmount);
			invoiceHeader.setPreTaxAmount(preTaxAmount);
			invoiceHeader.setTrip(trip);
			invoiceHeader.setParkingAmount(parkingAmt);
			invoiceHeader.setTollAmount(tollAmt);
			//invoiceHeader.setServiceTaxPercent(5.6);
			invoiceHeader.setTotalAmount(totalAmount);
			invoiceHeader.setKmsTravelled(totalKms);
			invoiceHeader.setMinsTravelled(totalMins);
			invoiceHeader.setOtherAllowance(otherAlwnc);
			if((invoiceNumber != null) && (! invoiceNumber.isEmpty())){
				invoiceHeader.setInvoiceNumber(invoiceNumber);
			}
			else{
				invoiceHeader.setInvoiceNumber(getInvNumber(tariff.getBase().getOrg(), em));
			}

			boolean isSendInvFlag = isSendInvoice(trip.getBookingReq().getPaymentOption(), invGenerateFor, billingRule);

			invoiceHeader.setSendInvoice(isSendInvFlag);
			em.persist(invoiceHeader);

			//Fill the InvoiceItem.
			// add type = 1, baseRate, amount to InvoiceItem
			invoiceItem.setType(1);
			invoiceItem.setBaseRate(tariff.getBaseRate());
			invoiceItem.setAmount(baseAmount);
			invoiceItem.setInvoiceItemVerbiage("Rental for "+tariff.getServiceType().getServiceTypeName());
			invoiceItem.setInvoiceRateVerbiage(" @Rs. "+tariff.getBaseRate());

			//Fill the InvoiceItem for driver allowance
			logger.info("-------------- b4 driver allowance");
			if(tariff.getDriverAllowance() > 0.0){
				logger.info("-------------- creating driver allowance invoiceitem");

				driverVerbiageInvoiceItem = new InvoiceItem();
				driverVerbiageInvoiceItem.setType(2);
				driverVerbiageInvoiceItem.setUsageKmRate(tariff.getNormalFarePerKm());
				driverVerbiageInvoiceItem.setUsageKms(totalKms);
				driverVerbiageInvoiceItem.setAmount(tariff.getDriverAllowance());
				driverVerbiageInvoiceItem.setDriverAllowance(tariff.getDriverAllowance());
				driverVerbiageInvoiceItem.setInvoiceRateVerbiage(" Rs. "+tariff.getDriverAllowance());
				driverVerbiageInvoiceItem.setInvoiceItemVerbiage("Driver Allowance");

			}
			//Fill the InvoiceItem for Flat Rate.
			if(flatAmount > 0.0){

				flatRateInvoiceItem = new InvoiceItem();
				flatRateInvoiceItem.setType(3);
				flatRateInvoiceItem.setOverageKms(0);
				flatRateInvoiceItem.setOverageKmRate(0);
				flatRateInvoiceItem.setOverageHours(0);
				flatRateInvoiceItem.setOverageHrRate(0.0);
				flatRateInvoiceItem.setAmount(flatAmount);
				flatRateInvoiceItem.setInvoiceRateVerbiage(" Rs. "+ flatAmount);
				flatRateInvoiceItem.setInvoiceItemVerbiage("Flat Rate");


			} else {
				//Fill the InvoiceItem for Extra Kms.
				if(extraKms > 0.0){
					logger.info("-----------Check 1");
					overageKmsItem = new InvoiceItem();
					overageKmsItem.setType(3);
					overageKmsItem.setOverageKms((int) extraKms);
					overageKmsItem.setOverageKmRate(tariff.getExtraKmCost());
					overageKmsItem.setOverageHours(0);
					overageKmsItem.setOverageHrRate(0);
					overageKmsItem.setAmount(extraKmAmount);
					overageKmsItem.setInvoiceRateVerbiage(" @ Rs. "+tariff.getExtraKmCost()+" per Km");
					overageKmsItem.setInvoiceItemVerbiage("Extra Kms: "+ new DecimalFormat("###0.00").format(extraKms));
				}

				//Fill the InvoiceItem for Extra Mins.
				if(extraHrs > 0.0){
					logger.info("-----------Check 2");
					overageMinItem = new InvoiceItem();
					overageMinItem.setType(3);
					overageMinItem.setOverageKms(0);
					overageMinItem.setOverageKmRate(0);
					overageMinItem.setOverageHours(((int)extraHrs)*60);
					overageMinItem.setOverageHrRate(tariff.getExtraHrCost());
					overageMinItem.setAmount(extraHrsAmount);
					overageMinItem.setInvoiceRateVerbiage(" @ Rs. "+tariff.getExtraHrCost()+" per Hr");
					overageMinItem.setInvoiceItemVerbiage("Extra Hrs: "+ new DecimalFormat("###0.00").format(extraHrs));
				}
			}

			invoiceItem.setInvoice(invoiceHeader);
			em.persist(invoiceItem);

			if(flatRateInvoiceItem != null){
				flatRateInvoiceItem.setInvoice(invoiceHeader);
				em.persist(flatRateInvoiceItem);
			}

			if (overageKmsItem != null){
				overageKmsItem.setInvoice(invoiceHeader);
				em.persist(overageKmsItem);
			}

			if (overageMinItem != null) {
				overageMinItem.setInvoice(invoiceHeader);
				em.persist(overageMinItem);
			}

			if((driverVerbiageInvoiceItem != null)) {
				logger.info("driver allowance item added");
				driverVerbiageInvoiceItem.setInvoice(invoiceHeader);
				em.persist(driverVerbiageInvoiceItem);
			}

			//et.commit();

			//updateInvoiceNumber(invoiceNumber, tariff.getBase().getOrg());
			String formattedTotalKm = new DecimalFormat(strDecimalFormat).format(totalKms);
			jsonObj.put("Response","Success");
			jsonObj.put("TotalAmount", String.valueOf(totalAmount));
			jsonObj.put("TotalKms", formattedTotalKm);
			jsonObj.put("TotalMins", totalMins);

			response=jsonObj.toString();
		}
		catch(Exception e){
			logger.error("*****Exception in  Create Invoice for Package :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));

			response = "Error";
		}
		finally{

			/*if(et.isActive()){
				et.rollback();
			}*/
		}
		logger.info("--Response From create invoice for Package----: "+response);
		return response;
	}

	//Following method is used for Hourly Packages invoice calculation.
	private String createInvoiceForHourlyPackages(Trip trip, BillingRule billingRule, long totalMins, double totalKms, Tariff tariff, double tollAmt, double parkingAmt,String invoiceNumber, String invGenerateFor, EntityManager em){
		String response = null, stateName = "";

		double amount = 0.0, actualKms = 0.0, billableKms = 0.0, finalKms = 0.0, extraKms = 0.0;
		double extraKmAmount = 0.0, flatAmount = 0.0;
		double igstAmt = 0.0, cgstAmt = 0.0, sgstAmt = 0.0;
		double preTaxAmount = 0.0, taxAmount = 0.0, totalAmount = 0.0, sbCessTaxAmount = 0.0, kkCessTaxAmount = 0.0;

		int actualHrs = 0, billableHrs = 0, finalHrs = 0;

		//EntityTransaction et = em.getTransaction();

		invoiceHeader = new InvoiceHeader();
		InvoiceItem invoiceItem = new InvoiceItem();
		InvoiceItem overageKmsItem = null;
		InvoiceItem flatRateInvoiceItem = null;
		JSONObject jsonObj = new JSONObject();

		try{
			//long hrs = totalMins/60;
			//long mins=totalMins%60;
			/*if (mins != 0) {
				actualHrs=hrs+1;
			} else {
				actualHrs=hrs;
			}*/
			if(totalMins <= 30){
				actualHrs = 1;
			}
			else{
				actualHrs = ((int)totalMins)/60;

				int actualMin = ((int)totalMins) % 60;

				if(actualMin > 0){
					if(actualMin < 30){
						actualHrs = ((int)actualHrs);
					}
					else{
						actualHrs = ((int)actualHrs) + 1;
					}
				}
				logger.info("--------Actual Hrs: " + actualHrs + " --- Minutes: "+ actualMin);
			}

			actualKms = totalKms;

			if("CORPORATE".equalsIgnoreCase(invGenerateFor)){

				if (billingRule != null) {
					logger.info("\n\n\n \t\t\t billing rule type--------:"+billingRule.getRuleType());

					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Id is:"+billingRule.getRuleId());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule flat amount is:"+billingRule.getFlatRate());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Added Km is:"+billingRule.getAddedKm());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Added Hrs is:"+billingRule.getAddedHour());

					billableKms = actualKms + billingRule.getAddedKm();
					billableHrs = actualHrs + billingRule.getAddedHour();
					logger.info("-------Billable Km: "+billableKms+" Billable Hrs: "+billableHrs);

					if (billingRule.getFlatRate() > 0.0){ // its flate rate calc, no need to consider extra kms and extra hrs.
						flatAmount = billingRule.getFlatRate();
						finalHrs = (int)tariff.getServiceType().getMinHr();
						finalKms = tariff.getServiceType().getMinKm();
					}
					else{
						if(tariff.getServiceType().getMinKm() < billableKms){
							finalKms = billableKms;
						} else {
							finalKms = tariff.getServiceType().getMinKm();
						}

						if(tariff.getServiceType().getMinHr() < billableHrs){
							finalHrs = billableHrs;
						} else {
							finalHrs = (int)tariff.getServiceType().getMinHr();
						}
					}

				}
				else{
					logger.info("---------Billing Rule not found.");
					billableKms = actualKms;
					billableHrs = actualHrs;

					if(tariff.getServiceType().getMinKm() < billableKms){
						finalKms = billableKms;
					} else {
						finalKms = tariff.getServiceType().getMinKm();
					}


					if(tariff.getServiceType().getMinHr() < billableHrs){
						finalHrs = billableHrs;
					} else {
						finalHrs = (int)tariff.getServiceType().getMinHr();
					}
				}

			}
			else{

				billableKms = actualKms;
				billableHrs = actualHrs;

				if(tariff.getServiceType().getMinKm() < billableKms){
					finalKms = billableKms;
				} else {
					finalKms = tariff.getServiceType().getMinKm();
				}

				if(tariff.getServiceType().getMinHr() < billableHrs){
					finalHrs = billableHrs;
				} else {
					finalHrs = (int)tariff.getServiceType().getMinHr();
				}
			}

			if(finalKms > (tariff.getIncludedKms() * finalHrs)){
				//Amount = (hourlyRate * roundedHours)  + (actualKms - (included kms*roundedHours) * extra_km_cost
				amount = (tariff.getHourlyRate() * finalHrs);
				extraKms = finalKms - (tariff.getIncludedKms() * finalHrs);
				extraKmAmount = extraKms * tariff.getExtraKmCost();

			}
			else{
				//Amount = (hourlyRate*roundedHours)
				amount = tariff.getHourlyRate() * finalHrs;
			}

			logger.info("-------Final Kms: "+finalKms + " Final Hrs: "+finalHrs);
			logger.info("---------Amount: "+amount+" Toll: "+tollAmt+" Parking Amt: "+parkingAmt+" ExtraKmsAmount: "+extraKmAmount + " Other Allowance: "+otherAlwnc);
			preTaxAmount = amount + tollAmt + parkingAmt +extraKmAmount+ otherAlwnc + flatAmount;
			logger.info("-------Pre tax amount: "+preTaxAmount);

			double tax = calculateGST(preTaxAmount,em,trip);
			totalAmount = preTaxAmount + tax;
			totalAmount = Math.round(totalAmount);
			logger.info("+++++++++++Total amount: "+totalAmount);

			//Fill the InvoiceHeader.
			//et.begin();
			invoiceHeader.setInvoiceDate(new Date(System.currentTimeMillis()));
			//invoiceHeader.setServiceTaxAmount(taxAmount);
			//invoiceHeader.setSbCessTaxAmount(sbCessTaxAmount);
			//invoiceHeader.setKkCessTaxAmount(kkCessTaxAmount);
			invoiceHeader.setPreTaxAmount(preTaxAmount);
			invoiceHeader.setTrip(trip);
			invoiceHeader.setParkingAmount(parkingAmt);
			invoiceHeader.setTollAmount(tollAmt);
			//invoiceHeader.setServiceTaxPercent(5.6);
			invoiceHeader.setTotalAmount(totalAmount);
			invoiceHeader.setKmsTravelled(finalKms);
			invoiceHeader.setMinsTravelled(totalMins);
			invoiceHeader.setOtherAllowance(otherAlwnc);
			if((invoiceNumber != null) && (! invoiceNumber.isEmpty())){
				invoiceHeader.setInvoiceNumber(invoiceNumber);
			}
			else{
				invoiceHeader.setInvoiceNumber(getInvNumber(tariff.getBase().getOrg(), em));
			}

			boolean isSendInvFlag = isSendInvoice(trip.getBookingReq().getPaymentOption(), invGenerateFor, billingRule);

			invoiceHeader.setSendInvoice(isSendInvFlag);
			em.persist(invoiceHeader);

			invoiceItem.setType(1);
			invoiceItem.setBaseRate(0);
			invoiceItem.setAmount(amount);
			invoiceItem.setInvoiceItemVerbiage("Rental for "+tariff.getServiceType().getServiceTypeName());
			invoiceItem.setInvoiceRateVerbiage(finalHrs +" @Rs. "+tariff.getHourlyRate()+" per Hour");

			if(flatAmount > 0.00){
				flatRateInvoiceItem = new InvoiceItem();
				flatRateInvoiceItem.setType(3);
				flatRateInvoiceItem.setOverageKms(0);
				flatRateInvoiceItem.setOverageKmRate(0);
				flatRateInvoiceItem.setOverageHours(0);
				flatRateInvoiceItem.setOverageHrRate(0.0);
				flatRateInvoiceItem.setAmount(flatAmount);
				flatRateInvoiceItem.setInvoiceRateVerbiage(" Rs. "+ flatAmount);
				flatRateInvoiceItem.setInvoiceItemVerbiage("Flat Rate");
			}
			else if(extraKms > 0.0){
				logger.info("-----------Check 1");
				overageKmsItem = new InvoiceItem();
				overageKmsItem.setType(3);
				overageKmsItem.setOverageKms((int) extraKms);
				overageKmsItem.setOverageKmRate(tariff.getExtraKmCost());
				overageKmsItem.setOverageHours(0);
				overageKmsItem.setOverageHrRate(0);
				overageKmsItem.setAmount(extraKmAmount);
				overageKmsItem.setInvoiceRateVerbiage(" @ Rs. "+tariff.getExtraKmCost()+" per Km");
				overageKmsItem.setInvoiceItemVerbiage("Extra Kms: "+ new DecimalFormat("###0.00").format(extraKms));
			}

			invoiceItem.setInvoice(invoiceHeader);
			em.persist(invoiceItem);

			if (overageKmsItem != null){
				overageKmsItem.setInvoice(invoiceHeader);
				em.persist(overageKmsItem);
			}

			if(flatRateInvoiceItem != null){
				flatRateInvoiceItem.setInvoice(invoiceHeader);
				em.persist(flatRateInvoiceItem);
			}
			//et.commit();
			//updateInvoiceNumber(invoiceNumber, tariff.getBase().getOrg());
			String formattedTotalKm = new DecimalFormat(strDecimalFormat).format(totalKms);
			jsonObj.put("Response","Success");
			jsonObj.put("TotalAmount", String.valueOf(totalAmount));
			jsonObj.put("TotalKms", formattedTotalKm);
			jsonObj.put("TotalMins", totalMins);

			response=jsonObj.toString();
		}
		catch(Exception e){
			logger.error("*****Exception in  Create Invoice for Hourly Packages :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));

			response = "Error";
		}
		finally{

			/*if(et.isActive()){
				et.rollback();
			}*/
		}
		logger.info("--Response From create invoice for Hourly Packages ----: "+response);
		return response;
	}

	//Following method is used for LOCAL TRANSFER invoice calculation.
	private String createInvoiceForLocalTransfer(Trip trip, BillingRule billingRule, long totalMins, double totalKms, Tariff tariff, double tollAmt, double parkingAmt,String invoiceNumber, String invGenerateFor, EntityManager em){

		String response = null, stateName = "";
		double amount = 0.0, actualKms = 0.0, billableKms = 0.0, finalKms = 0.0, extraKms = 0.0, grandTotal = 0.0;
		double extraKmAmount = 0.0, extraMinAmount = 0.0, baseAmount = 0.0, flatAmount = 0.0;
		double preTaxAmount = 0.0, taxAmount = 0.0, totalAmount = 0.0, sbCessTaxAmount = 0.0, kkCessTaxAmount = 0.0;
		double igstAmt = 0.0, cgstAmt = 0.0, sgstAmt = 0.0;
		double actualMin = 0.0, billableMin = 0.0, finalMin = 0.0, extraMin = 0.0, minMinutes = 0.0;

		//EntityTransaction et = em.getTransaction();

		invoiceHeader = new InvoiceHeader();
		InvoiceItem invoiceItem = new InvoiceItem();
		InvoiceItem overageKmsItem = null;
		InvoiceItem overageMinItem = null;
		InvoiceItem flatRateInvoiceItem = null;

		JSONObject jsonObj = new JSONObject();

		try{
			actualMin = totalMins;
			actualKms = totalKms;

			minMinutes = tariff.getServiceType().getMinHr() * 60;

			//logger.info("\n\n\n \t\t\t billing rule type--------:"+billingRule.getRuleType());

			//logger.info("\n\n\n \t\t\t billing rule for invoice------rule Id is:"+billingRule.getRuleId());

			logger.info("----------tariff Min Kms: "+tariff.getServiceType().getMinKm()+" Min Minutes: "+minMinutes);

			if("CORPORATE".equalsIgnoreCase(invGenerateFor)){

				if (billingRule != null) {

					logger.info("\n\n\n \t\t\t billing rule type--------:"+billingRule.getRuleType());

					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Id is:"+billingRule.getRuleId());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule flat amount is:"+billingRule.getFlatRate());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Added Km is:"+billingRule.getAddedKm());
					logger.info("\n\n\n \t\t\t billing rule for invoice------rule Added Hrs is:"+billingRule.getAddedHour());

					billableKms = actualKms + billingRule.getAddedKm();
					billableMin = actualMin + (billingRule.getAddedHour() * 60);
					logger.info("-------Billable Km: "+billableKms+" Billable Min: "+billableMin);

					if (billingRule.getFlatRate() > 0.0){ // its flate rate calc, no need to consider extra kms and extra hrs.
						flatAmount = billingRule.getFlatRate();
					} else {

						if(tariff.getServiceType().getMinKm() < billableKms){
							finalKms = billableKms - tariff.getServiceType().getMinKm();
						} else {
							// ignore it, no extra kms
						}


						if(minMinutes < billableMin){
							finalMin = billableMin - minMinutes;
						}
						else{
							// ignore it, no extra min
						}
					}
				}
				else {
					logger.info("---------Billing Rule not found.");
					billableKms = actualKms;
					billableMin = actualMin;

					if(tariff.getServiceType().getMinKm() < billableKms){
						finalKms = billableKms - tariff.getServiceType().getMinKm();
					} else {
						// ignore it, no extra kms
					}


					if(minMinutes < billableMin){
						finalMin = billableMin - minMinutes;
					}
					else{
						// ignore it, no extra min
					}
				}

				if (finalKms >  0.0) {

					extraKms = finalKms;
					extraKmAmount = extraKms * tariff.getNormalFarePerKm();

				}


				if (finalMin > 0.0) {

					extraMin = finalMin;
					extraMinAmount = extraMin * tariff.getNormalFarePerMin();

				}
			}
			else {

				billableKms = actualKms;
				billableMin = actualMin;

				if(tariff.getServiceType().getMinKm() < billableKms){
					finalKms = billableKms - tariff.getServiceType().getMinKm();
				} else {
					// ignore it, no extra kms
				}

				if (finalKms > 0.0){

					extraKms = finalKms;
					extraKmAmount = extraKms * tariff.getNormalFarePerKm();
				}


				if(minMinutes < billableMin){
					finalMin = billableMin - minMinutes;
				}
				else{
					// ignore it, no extra min
				}

				if (finalMin > 0.0){
					extraMin = finalMin;
					extraMinAmount = extraMin * tariff.getNormalFarePerMin();
				}

			}
			logger.info("-------Final Km: "+finalKms+" finalMin: "+finalMin);
			logger.info("-------Extra Km: "+extraKms+" ExtraMin: "+extraMin);
			logger.info("-------Extra Km amount: "+extraKmAmount+" Extra Min amount: "+extraMinAmount +" Flat Amt: "+flatAmount);

			baseAmount = tariff.getBaseRate();

			amount = baseAmount + extraKmAmount + extraMinAmount + flatAmount;

			preTaxAmount = amount + tollAmt + parkingAmt + otherAlwnc;
			logger.info("-------Pre tax amount: "+preTaxAmount);

			double tax = calculateGST(preTaxAmount,em,trip);
			totalAmount = preTaxAmount + tax;
			totalAmount = Math.round(totalAmount);
			logger.info("+++++++++++Total amount: "+totalAmount);

			//Fill the InvoiceHeader.
			//et.begin();

			invoiceHeader.setInvoiceDate(new Date(System.currentTimeMillis()));
			//invoiceHeader.setServiceTaxAmount(taxAmount);
			//invoiceHeader.setSbCessTaxAmount(sbCessTaxAmount);
			//invoiceHeader.setKkCessTaxAmount(kkCessTaxAmount);
			invoiceHeader.setPreTaxAmount(preTaxAmount);
			invoiceHeader.setTrip(trip);
			invoiceHeader.setParkingAmount(parkingAmt);
			invoiceHeader.setTollAmount(tollAmt);
			//invoiceHeader.setServiceTaxPercent(5.6);
			invoiceHeader.setTotalAmount(totalAmount);
			invoiceHeader.setKmsTravelled(totalKms);
			invoiceHeader.setMinsTravelled(totalMins);
			invoiceHeader.setOtherAllowance(otherAlwnc);
			if((invoiceNumber != null) && (! invoiceNumber.isEmpty())){
				invoiceHeader.setInvoiceNumber(invoiceNumber);
			}
			else{
				invoiceHeader.setInvoiceNumber(getInvNumber(tariff.getBase().getOrg(), em));
			}

			boolean isSendInvFlag = isSendInvoice(trip.getBookingReq().getPaymentOption(), invGenerateFor, billingRule);

			invoiceHeader.setSendInvoice(isSendInvFlag);
			em.persist(invoiceHeader);

			//Fill the InvoiceItem.
			// add type = 1, baseRate, amount to InvoiceItem
			invoiceItem.setType(1);
			invoiceItem.setBaseRate(tariff.getBaseRate());
			invoiceItem.setAmount(baseAmount);
			invoiceItem.setInvoiceItemVerbiage("Rental for "+tariff.getServiceType().getServiceTypeName());
			invoiceItem.setInvoiceRateVerbiage(" @ Rs. "+tariff.getBaseRate());

			//Fill the InvoiceItem for Flat Rate.
			if(flatAmount > 0.0){

				flatRateInvoiceItem = new InvoiceItem();
				flatRateInvoiceItem.setType(3);
				flatRateInvoiceItem.setOverageKms(0);
				flatRateInvoiceItem.setOverageKmRate(0);
				flatRateInvoiceItem.setOverageHours(0);
				flatRateInvoiceItem.setOverageHrRate(0.0);
				flatRateInvoiceItem.setAmount(flatAmount);
				flatRateInvoiceItem.setInvoiceRateVerbiage(" Rs. "+ flatAmount);
				flatRateInvoiceItem.setInvoiceItemVerbiage("Flat Rate");


			} else {
				//Fill the InvoiceItem for Extra Kms.
				if(extraKms > 0.0){
					logger.info("-----------Check 1");
					overageKmsItem = new InvoiceItem();
					overageKmsItem.setType(3);
					overageKmsItem.setOverageKms((int) extraKms);
					overageKmsItem.setOverageKmRate(tariff.getNormalFarePerKm());
					overageKmsItem.setOverageHours(0);
					overageKmsItem.setOverageHrRate(0);
					overageKmsItem.setAmount(extraKmAmount);
					overageKmsItem.setInvoiceRateVerbiage(" @ Rs. "+tariff.getNormalFarePerKm()+" per Km");
					overageKmsItem.setInvoiceItemVerbiage("Extra Kms: "+ new DecimalFormat("###0.00").format(extraKms));
				}

				//Fill the InvoiceItem for Extra Mins.
				if(extraMin > 0.0){
					logger.info("-----------Check 2");
					overageMinItem = new InvoiceItem();
					overageMinItem.setType(3);
					overageMinItem.setOverageKms(0);
					overageMinItem.setOverageKmRate(0);
					overageMinItem.setOverageHours((int)extraMin);
					overageMinItem.setOverageHrRate(tariff.getNormalFarePerMin());
					overageMinItem.setAmount(extraMinAmount);
					overageMinItem.setInvoiceRateVerbiage(" @ Rs. "+tariff.getNormalFarePerMin()+" per Min");
					overageMinItem.setInvoiceItemVerbiage("Extra Min: "+ new DecimalFormat("###0.00").format(extraMin));
				}
			}

			invoiceItem.setInvoice(invoiceHeader);
			em.persist(invoiceItem);

			if(flatRateInvoiceItem != null){
				flatRateInvoiceItem.setInvoice(invoiceHeader);
				em.persist(flatRateInvoiceItem);
			}

			if (overageKmsItem != null){
				overageKmsItem.setInvoice(invoiceHeader);
				em.persist(overageKmsItem);
			}

			if (overageMinItem != null) {
				overageMinItem.setInvoice(invoiceHeader);
				em.persist(overageMinItem);
			}

			//et.commit();

			//updateInvoiceNumber(invoiceNumber, tariff.getBase().getOrg());
			String formattedTotalKm = new DecimalFormat(strDecimalFormat).format(totalKms);
			jsonObj.put("Response","Success");
			jsonObj.put("TotalAmount", String.valueOf(totalAmount));
			jsonObj.put("TotalKms", formattedTotalKm);
			jsonObj.put("TotalMins", totalMins);

			response=jsonObj.toString();
		}
		catch(Exception e){
			logger.error("*****Exception in  Create Invoice for Local Transfer :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));

			response = "Error";
		}
		finally{

			/*if(et.isActive()){
				et.rollback();
			}*/
		}
		logger.info("--Response From create invoice for Local Transfer----: "+response);
		return response;
	}

	//Following method is used for generate invoice for OUTSTATION TRANSFER.
	/**
	 * Calculation:
	 * 
	 * Total Amount = base rate + flat rate(If Present) + Driver Allowance(If greater than 0.0) + parking Amount + toll Amount + TAX(If applicable).
	 */
	private String createInvoiceForOutstationTransfer(Trip trip, BillingRule billingRule, long totalMins, double totalKms, Tariff tariff, double tollAmt, double parkingAmt,String invoiceNumber, String invGenerateFor, EntityManager em){
		logger.info("----------:Invoice Outstion Transfer:--------");
		String response = "";
		String stateName = "";
		double preTaxAmount = 0.0, taxAmount = 0.0, totalAmount = 0.0, sbCessTaxAmount = 0.0, kkCessTaxAmount = 0.0;
        double igstAmt = 0.0, cgstAmt = 0.0, sgstAmt = 0.0;
		
		JSONObject jsonObj = new JSONObject();

		invoiceHeader = new InvoiceHeader();
		InvoiceItem invoiceItem = new InvoiceItem();
		InvoiceItem driverVerbiageInvoiceItem = null;
		//EntityTransaction et = em.getTransaction();

		try{

			//No need to check for retail customer or corporate.

			logger.info("------------- Base Rate: "+tariff.getBaseRate()+" Driver Allowance: "+tariff.getDriverAllowance());
			logger.info("--------------Parking Amount: "+parkingAmt + " Toll Amount: "+tollAmt);

			preTaxAmount = tariff.getBaseRate() + tariff.getDriverAllowance() + parkingAmt + tollAmt + otherAlwnc;

			logger.info("--------------PreTaxAmount: "+preTaxAmount);

			double tax = calculateGST(preTaxAmount,em,trip);
			totalAmount = preTaxAmount + tax;
			totalAmount = Math.round(totalAmount);
			logger.info("+++++++++++Total amount: "+totalAmount);

			//Fill the InvoiceHeader.
			//et.begin();

			invoiceHeader.setInvoiceDate(new Date(System.currentTimeMillis()));
			//invoiceHeader.setServiceTaxAmount(taxAmount);
			//invoiceHeader.setSbCessTaxAmount(sbCessTaxAmount);
			//invoiceHeader.setKkCessTaxAmount(kkCessTaxAmount);
			invoiceHeader.setPreTaxAmount(preTaxAmount);
			invoiceHeader.setTrip(trip);
			invoiceHeader.setParkingAmount(parkingAmt);
			invoiceHeader.setTollAmount(tollAmt);
			//invoiceHeader.setServiceTaxPercent(5.6);
			invoiceHeader.setTotalAmount(totalAmount);
			invoiceHeader.setKmsTravelled(totalKms);
			invoiceHeader.setMinsTravelled(totalMins);
			invoiceHeader.setOtherAllowance(otherAlwnc);
			if((invoiceNumber != null) && (! invoiceNumber.isEmpty())){
				invoiceHeader.setInvoiceNumber(invoiceNumber);
			}
			else{
				invoiceHeader.setInvoiceNumber(getInvNumber(tariff.getBase().getOrg(),em));
			}
			boolean isSendInvFlag = isSendInvoice(trip.getBookingReq().getPaymentOption(), invGenerateFor, billingRule);
			invoiceHeader.setSendInvoice(isSendInvFlag);
			em.persist(invoiceHeader);

			invoiceItem.setType(1);
			invoiceItem.setBaseRate(tariff.getBaseRate());
			invoiceItem.setAmount(tariff.getBaseRate());
			invoiceItem.setInvoiceItemVerbiage("Rental for "+tariff.getServiceType().getServiceTypeName());
			invoiceItem.setInvoiceRateVerbiage(" @Rs. "+ tariff.getBaseRate());

			if(tariff.getDriverAllowance() > 0.0){
				logger.info("-------------- creating driver allowance invoiceitem");

				driverVerbiageInvoiceItem = new InvoiceItem();
				driverVerbiageInvoiceItem.setType(2);
				driverVerbiageInvoiceItem.setUsageKmRate(0);
				driverVerbiageInvoiceItem.setUsageKms(0);
				driverVerbiageInvoiceItem.setAmount(tariff.getDriverAllowance());
				driverVerbiageInvoiceItem.setDriverAllowance(tariff.getDriverAllowance());
				driverVerbiageInvoiceItem.setInvoiceRateVerbiage(" Rs. "+tariff.getDriverAllowance());
				driverVerbiageInvoiceItem.setInvoiceItemVerbiage("Driver Allowance");

			}

			invoiceItem.setInvoice(invoiceHeader);
			em.persist(invoiceItem);

			if((driverVerbiageInvoiceItem != null)) {
				logger.info("---------------driver allowance item added");
				driverVerbiageInvoiceItem.setInvoice(invoiceHeader);
				em.persist(driverVerbiageInvoiceItem);
			}
			//et.commit();

			//updateInvoiceNumber(invoiceNumber,tariff.getBase().getOrg());
			String formattedTotalKm = new DecimalFormat(strDecimalFormat).format(totalKms);
			jsonObj.put("Response","Success");
			jsonObj.put("TotalAmount", String.valueOf(totalAmount));
			jsonObj.put("TotalKms", formattedTotalKm);
			jsonObj.put("TotalMins", totalMins);

			response=jsonObj.toString();


		}
		catch(Exception e){
			logger.error("*****Exception in  Create Invoice for Outstation Transfer :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));

			response = "Error";
		}
		finally{

			/*if(et.isActive()){
				et.rollback();
			}*/
		}
		logger.info("-------Response From create invoice for Outstation Transfer----: "+response);

		return response;
	}

	//Following method is used for generate invoice for OUTSTATION.
	/**
	 * For outstation Customer type(Corporate or Retail) it does not make any sense.
	 * */
	private String createInvoiceForOutstation(Trip trip, BillingRule billingRule, long totalMins, double totalKms, Tariff tariff, double tollAmt, double parkingAmt,String invoiceNumber, String invGenerateFor, long noOfDays ,EntityManager em){
		logger.info("----------:Invoice Outstion:--------");
		String response = "";
		String stateName = "";
		double finalKm = 0.0,totalMinKms = 0.0, igstAmt = 0.0, cgstAmt = 0.0, sgstAmt = 0.0;
		double preTaxAmount = 0.0, taxAmount = 0.0, totalAmount = 0.0, sbCessTaxAmount = 0.0, kkCessTaxAmount = 0.0, flatRate = 0.0;

		JSONObject jsonObj = new JSONObject();

		invoiceHeader = new InvoiceHeader();
		InvoiceItem invoiceItem = new InvoiceItem();
		InvoiceItem driverVerbiageInvoiceItem = null;

		//EntityTransaction et = em.getTransaction();

		try{

			//Psudo code.
			/**
			 * totalMinKm = tariff.getServiceType().getMinKm() * noOfDays.
			 * ActualKm = totalKm.
			 * finalKm = 0;
			 * 
			 * if(totalMinKm < totalKms){
			 *    finalKm = totalKms;
			 * }
			 * else{
			 * 	  finalKm = totalMinKm;
			 * }
			 * 
			 * totalAmt = (finalKm * tariff.getNormalFarePerKm) + (tariff.getDriverAllowance * noOfDays)
			 * 
			 */
			totalMinKms = (tariff.getServiceType().getMinKm() * noOfDays);
			logger.info("---------Total minimun kms: "+totalMinKms);

			if(totalMinKms < totalKms){
				finalKm = totalKms;
			}
			else{
				finalKm = totalMinKms;
			}
			logger.info("----------Final Kms: "+finalKm+" Fare per km: "+tariff.getNormalFarePerKm());
			logger.info("----------No. of days: "+noOfDays+" Driver allowance per day: "+tariff.getDriverAllowance());

			preTaxAmount = (finalKm * tariff.getNormalFarePerKm()) + (tariff.getDriverAllowance() * noOfDays) + parkingAmt + tollAmt + otherAlwnc;
			logger.info("--------------PreTaxAmount: "+preTaxAmount);

			double tax = calculateGST(preTaxAmount,em,trip);
			totalAmount = preTaxAmount + tax;
			
			totalAmount = Math.round(totalAmount);
			logger.info("+++++++++++Total amount: "+totalAmount);

			//Fill the InvoiceHeader.
			//et.begin();

			invoiceHeader.setInvoiceDate(new Date(System.currentTimeMillis()));
			//invoiceHeader.setServiceTaxAmount(taxAmount);
			//invoiceHeader.setSbCessTaxAmount(sbCessTaxAmount);
			//invoiceHeader.setKkCessTaxAmount(kkCessTaxAmount);
			invoiceHeader.setPreTaxAmount(preTaxAmount);
			invoiceHeader.setTrip(trip);
			invoiceHeader.setParkingAmount(parkingAmt);
			invoiceHeader.setTollAmount(tollAmt);
			//invoiceHeader.setServiceTaxPercent(5.6);
			invoiceHeader.setTotalAmount(totalAmount);
			invoiceHeader.setKmsTravelled(finalKm);
			invoiceHeader.setMinsTravelled(totalMins);
			invoiceHeader.setOtherAllowance(otherAlwnc);
			if((invoiceNumber != null) && (! invoiceNumber.isEmpty())){
				invoiceHeader.setInvoiceNumber(invoiceNumber);
			}
			else{
				invoiceHeader.setInvoiceNumber(getInvNumber(tariff.getBase().getOrg(),em));
			}
			invoiceHeader.setSendInvoice(false);
			em.persist(invoiceHeader);

			invoiceItem.setType(1);
			invoiceItem.setBaseRate(0.0);
			invoiceItem.setAmount(finalKm * tariff.getNormalFarePerKm());
			invoiceItem.setInvoiceItemVerbiage("Rental for " +tariff.getServiceType().getServiceTypeName()+ " - Total Kms: "+finalKm);
			invoiceItem.setInvoiceRateVerbiage(" @ Rs. "+ tariff.getNormalFarePerKm()+ " per km");

			if(tariff.getDriverAllowance() > 0.0){
				logger.info("-------------- creating driver allowance invoiceitem");

				driverVerbiageInvoiceItem = new InvoiceItem();
				driverVerbiageInvoiceItem.setType(2);
				driverVerbiageInvoiceItem.setUsageKmRate(0);
				driverVerbiageInvoiceItem.setUsageKms(0);
				driverVerbiageInvoiceItem.setAmount(tariff.getDriverAllowance()*noOfDays);
				driverVerbiageInvoiceItem.setDriverAllowance(tariff.getDriverAllowance());
				driverVerbiageInvoiceItem.setInvoiceRateVerbiage(" @ Rs. "+tariff.getDriverAllowance()+" per day");
				driverVerbiageInvoiceItem.setInvoiceItemVerbiage("Driver Allowance for No. of days: "+noOfDays);

			}

			invoiceItem.setInvoice(invoiceHeader);
			em.persist(invoiceItem);

			if((driverVerbiageInvoiceItem != null)) {
				logger.info("---------------driver allowance item added");
				driverVerbiageInvoiceItem.setInvoice(invoiceHeader);
				em.persist(driverVerbiageInvoiceItem);
			}
			//et.commit();

			//updateInvoiceNumber(invoiceNumber,tariff.getBase().getOrg());
			String formattedTotalKm = new DecimalFormat(strDecimalFormat).format(finalKm);
			jsonObj.put("Response","Success");
			jsonObj.put("TotalAmount", String.valueOf(totalAmount));
			jsonObj.put("TotalKms", formattedTotalKm);
			jsonObj.put("TotalMins", totalMins);

			response=jsonObj.toString();
		}
		catch(Exception e){
			logger.error("*****Exception in  Create Invoice for Outstation :"+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));

			response = "Error";
		}
		finally{

			/*if(et.isActive()){
				et.rollback();
			}*/
		}
		logger.info("-------Response From create invoice for Outstation----: "+response);

		return response;
	}



	
	private String getInvNumber(Organisation org, EntityManager em){
		String invNumber = null;
		try{
			invNumber = AdCommon.generateInvoiceNumberForOrg(org, em);
			logger.info("Invoice number---------"+invNumber);
		}
		catch(Exception e1){
			logger.error("Normal Exception:"+e1);
			logger.error(GTCommon.getStackTraceAsString(e1));
		}
		return invNumber;
	}

	private void findFileToDelete(String invoiceFilePath,long tripId){
		try {                       
			String path = invoiceFilePath;
			String filename;
			Process pr = null;
			Runtime run = Runtime.getRuntime();
			ArrayList<String> list = new ArrayList<String>();      
			try {
				pr = run.exec("ls "+path);

				try (BufferedReader processOutputReader = new BufferedReader(
						new InputStreamReader(pr.getInputStream()));)
				{
					String readLine;

					while ((readLine = processOutputReader.readLine()) != null)
					{
						list.add(readLine);
					}

					try {
						pr.waitFor();
					} catch (InterruptedException e) {

						logger.error("Exception " + e.getMessage());
						logger.error(GTCommon.getStackTraceAsString(e));
					}
				} 
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				logger.error("Exception "+ e1.getMessage());
				logger.error(GTCommon.getStackTraceAsString(e1));
			}

			for (int j=0; j < list.size() ; j++ ) {
				filename = list.get(j);
				logger.info(filename);
				if(filename.startsWith("inv_"+tripId)) {

					String filePath = path+"/"+filename;
					File f = new File(filePath);
					logger.info("File------------------------"+f.toString());

					if(f.exists()){
						f.delete();
					}
				}
			}
		}catch(Exception e){
			logger.error("Exception "+ e.getMessage());
			logger.error(GTCommon.getStackTraceAsString(e));
		}
	}

	private long calculateTimeDifference(Date Datein,Date Dateout){
		logger.info("-----------Date in--------------"+Datein);
		logger.info("--------Date out-----------------"+Dateout);
		long totalMins=0;
		try {

			//in milliseconds
			long diff = Dateout.getTime() - Datein.getTime();
			//			logger.info("-----differnce in time------------"+diff);
			if(diff!=0){
				long diffSeconds = diff / 1000 % 60;
				long diffMinutes = diff / (60 * 1000) % 60;
				long diffHours = diff / (60 * 60 * 1000) % 24;
				long diffDays = diff / (24 * 60 * 60 * 1000);

				System.out.print(diffDays + " days, ");
				System.out.print(diffHours + " hours, ");
				System.out.print(diffMinutes + " minutes, ");
				System.out.print(diffSeconds + " seconds.");
				totalMins=(diffDays*24*60)+(diffHours*60)+diffMinutes;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}


		return totalMins;

	}


	// Following method is used for getting No. of Days between date range.
	public long getNoOfDays(Date Datein,Date Dateout){
		logger.info("-----------Date in--------------"+Datein);
		logger.info("--------Date out-----------------"+Dateout);
		int diffDays=0;
		try {
			Calendar calOut = Calendar.getInstance();
			calOut.setTime(Dateout);

			int dayOut = calOut.get(Calendar.DATE);

			logger.info("Day out: "+calOut.get(Calendar.DATE));

			Calendar calIn = Calendar.getInstance();
			calIn.setTime(Datein);
			int dayIn = calIn.get(Calendar.DATE);

			logger.info("Day in: "+calIn.get(Calendar.DATE));


			int diff = dayOut - dayIn;
			if(diff!=0){
				diffDays = diff + 1;
				//logger.info("++++++++++++++No. of Days: "+diffDays);
			}
			else{
				diffDays = 1;
				//logger.info("++++++++++++++ else No. of Days: "+diffDays);
			}
		} catch (Exception e) {
			logger.info("++++++++++++++Exception in getNoOfDays: "+e);
			e.printStackTrace();
		}
		return diffDays;
	}

	//Following method is used to check double value is 0 or not.
	public boolean checkDoubleValue(double value){
		boolean IsZero = true;

		double d1 = 0.00;

		int retval = Double.compare(value, d1);

		if(retval > 0) {
			IsZero = false;
		}
		else if(retval < 0) {
			IsZero = false;
		}
		else {
			IsZero = true;
		}
		return IsZero;
	}

	//Following method is used for getting flag for sendInvoice or not.
	public boolean isSendInvoice(int payOption, String invGenFor, BillingRule billingRule){

		boolean isSend = false;

		if(payOption == 1){ // 1 - Cash

			if("CORPORATE".equalsIgnoreCase(invGenFor)){
				if(billingRule != null){ 
					if(billingRule.getRuleType() == 2){// GSO to GSC
						isSend = true;
					}
					else{
						isSend = false;
					}
				}
				else{
					isSend = true;
				}
			}
			else{
				isSend = true;
			}
		}
		else{
			isSend = false;
		}

		return isSend;
	}
	
	
private double calculateGST(double preTaxAmount, EntityManager em,Trip trip) {
	 
	 double tax =0;
	
	 boolean exemptFlag = false;
	 String stateName;
	 double cgstAmount,sgstAmount,igstAmount;
	 
	 Corporate corp = trip.getBookingReq().getTariff().getCorporate();
	 if ((trip.getBookingReq().getTariff().getCorporate() != null) && trip.getBookingReq().getTariff().getCorporate().getGSTIN() != null) {
		 //corporate credit
			 stateName = trip.getBookingReq().getTariff().getBase().getState();
			 String corpStateName = (corp.getState() != null) ? corp.getState().trim() : "";
             if (!trip.getBookingReq().getTariff().getCorporate().getIsExempt()) {
            		if ((stateName != null) && (stateName.trim().length() > 0)) {
            			if (stateName.equalsIgnoreCase(corpStateName)) {
            				sgstAmount= (preTaxAmount * 2.5) / 100;
            				cgstAmount= (preTaxAmount * 2.5) / 100;
            				invoiceHeader.setSGSTPercent(2.5);
            				invoiceHeader.setSGSTAmount(sgstAmount);
            				invoiceHeader.setCGSTPercent(2.5);
            				invoiceHeader.setCGSTAmount(cgstAmount);
            				if ((trip.getBookingReq().getTariff().getBase().getGSTNumber() != null) && (trip.getBookingReq().getTariff().getBase().getGSTNumber().trim().length() > 0)){
                				invoiceHeader.setGSTN(trip.getBookingReq().getTariff().getBase().getGSTNumber());
            				} else {
            					invoiceHeader.setGSTN(CommonQueries.getPrimaryGSTN(trip.getBookingReq().getTariff().getBase().getOrg().getOrganisationId(), em));
            				}
                            tax = sgstAmount+cgstAmount;
            			}
            			else {
            				igstAmount = (preTaxAmount * 5 )/ 100;
             				invoiceHeader.setIGSTPercent(5.00);
            				invoiceHeader.setIGSTAmount(igstAmount);
            				tax = igstAmount;
            				if ((trip.getBookingReq().getTariff().getBase().getGSTNumber() != null) && (trip.getBookingReq().getTariff().getBase().getGSTNumber().trim().length() > 0)){
                				invoiceHeader.setGSTN(trip.getBookingReq().getTariff().getBase().getGSTNumber());
            				} else {
            					invoiceHeader.setGSTN(CommonQueries.getPrimaryGSTN(trip.getBookingReq().getTariff().getBase().getOrg().getOrganisationId(), em));
           					
            				}

            			}
            		}
             	} else {
             		tax = 0.0;
             	}
	 		}
	 		else {
   				if ((trip.getBookingReq().getTariff().getBase().getGSTNumber() != null) && (trip.getBookingReq().getTariff().getBase().getGSTNumber().trim().length() > 0)){
        				invoiceHeader.setGSTN(trip.getBookingReq().getTariff().getBase().getGSTNumber());
           				sgstAmount= (preTaxAmount * 2.5) / 100;
        				cgstAmount= (preTaxAmount * 2.5) / 100;
        				invoiceHeader.setSGSTPercent(2.5);
        				invoiceHeader.setSGSTAmount(sgstAmount);
        				invoiceHeader.setCGSTPercent(2.5);
        				invoiceHeader.setCGSTAmount(cgstAmount);
                        tax = sgstAmount+cgstAmount;
                    } else {
    					invoiceHeader.setGSTN(CommonQueries.getPrimaryGSTN(trip.getBookingReq().getTariff().getBase().getOrg().getOrganisationId(), em));
    					igstAmount = (preTaxAmount * 5 )/ 100;
	     				invoiceHeader.setIGSTPercent(5.00);
	       				invoiceHeader.setIGSTAmount(igstAmount);
	    				tax = igstAmount;
   				}
 	 		}
	
	 		return tax;
	}

		private List<InvoiceHeader> getInvoiceHeadersUsingTripId(long tripId, EntityManager em){
			List<InvoiceHeader> invoiceHeaderList = null;
			try{
				String ihObject="From InvoiceHeader where tripId="+tripId;
				logger.info("query----------"+ihObject);
				Query queryTogetInvoiceHeader = em.createQuery(ihObject);
				invoiceHeaderList = (List<InvoiceHeader>) queryTogetInvoiceHeader.getResultList();
				return invoiceHeaderList;
			}catch(NoResultException e){
				logger.error("Exception for no InvoiceHeader corresponding to trip id in request: trip id "+tripId );
				return null;
			}
			catch(Exception e1){
				logger.error("Normal Exception:"+e1);
				logger.error(GTCommon.getStackTraceAsString(e1));
				return null;
			}
		}
}
