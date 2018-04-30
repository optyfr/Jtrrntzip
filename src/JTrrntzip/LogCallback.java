package JTrrntzip;

interface LogCallback extends StatusCallback
{
	void StatusLogCallBack(int processId, String log);
}
