package com.adeona.ws;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class BwsSendInvoiceBean {

	@SerializedName("bookingRefNum")
	@Expose
	private String bookingRefNum;
	@SerializedName("organisationId")
	@Expose
	private Object organisationId;
	@SerializedName("tripId")
	@Expose
	private Object tripId;

	public BwsSendInvoiceBean() {

	}

	public BwsSendInvoiceBean(String bookingRefNum, Object organisationId,
			Object tripId) {

		this.bookingRefNum = bookingRefNum;
		this.organisationId = organisationId;
		this.tripId = tripId;
	}

	public String getBookingRefNum() {
		return bookingRefNum;
	}

	public void setBookingRefNum(String bookingRefNum) {
		this.bookingRefNum = bookingRefNum;
	}

	public Object getOrganisationId() {
		return organisationId;
	}

	public void setOrganisationId(Object organisationId) {
		this.organisationId = organisationId;
	}

	public Object getTripId() {
		return tripId;
	}

	public void setTripId(Object tripId) {
		this.tripId = tripId;
	}

	@Override
	public String toString() {
		return "BwsSendInvoiceBean [bookingRefNum=" + bookingRefNum
				+ ", organisationId=" + organisationId + ", tripId=" + tripId + "]";
	}

}
