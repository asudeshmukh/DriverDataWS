/*$Id: OTPCustNotificationBean.java,v 1.1 2017/05/03 06:06:38 rahulm Exp $*/
package com.adeona.ws;

public class OTPCustNotificationBean {

	long tripId;
	
	public OTPCustNotificationBean() {

	}
	
	public OTPCustNotificationBean(long tripId) {
		super();
		this.tripId = tripId;
	}
	
	public long getTripId() {
		return tripId;
	}

	public void setTripId(long tripId) {
		this.tripId = tripId;
	}

	@Override
	public String toString() {
		return "OTPCustNotificationBean [tripId=" + tripId + "]";
	}
	
}
