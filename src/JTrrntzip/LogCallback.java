package JTrrntzip;

interface LogCallback extends StatusCallback
{
	boolean isVerboseLogging();
	void StatusLogCallBack(String log);
}
