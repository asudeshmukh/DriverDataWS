package com.adeona.ws;

public class BwsInvoiceCustNotificationBean {

	public BwsInvoiceCustNotificationBean(String pdfFileName, long tripId) {
		super();
		this.pdfFileName = pdfFileName;
		this.tripId = tripId;
	}
	
	public BwsInvoiceCustNotificationBean() {

	}
	
	String pdfFileName;
	long tripId;
	
	public String getPdfFileName() {
		return pdfFileName;
	}
	public void setPdfFileName(String pdfFileName) {
		this.pdfFileName = pdfFileName;
	}
	public long getTripId() {
		return tripId;
	}
	public void setTripId(long tripId) {
		this.tripId = tripId;
	}

	@Override
	public String toString() {
		return "BwsInvoiceCustNotificationBean [pdfFileName=" + pdfFileName
				+ ", tripId=" + tripId + "]";
	}
	
	
	
}
