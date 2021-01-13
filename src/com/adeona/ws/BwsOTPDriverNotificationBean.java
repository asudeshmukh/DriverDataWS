package com.adeona.ws;

public class BwsOTPDriverNotificationBean {

	String OTP;
	String driverMobNo;
	
	public BwsOTPDriverNotificationBean(String otp, String driverMobNo) {
		super();
		OTP = otp;
		this.driverMobNo = driverMobNo;
	}	
	
	public BwsOTPDriverNotificationBean() {

	}	
	
	public String getOTP() {
		return OTP;
	}
	public void setOTP(String oTP) {
		OTP = oTP;
	}
	public String getDriverMobNo() {
		return driverMobNo;
	}
	public void setDriverMobNo(String driverMobNo) {
		this.driverMobNo = driverMobNo;
	}
	
}
