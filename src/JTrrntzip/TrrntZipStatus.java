package JTrrntzip;

import java.io.Serializable;

public enum TrrntZipStatus implements Serializable
{
	ValidTrrntzip,
	CorruptZip,
	NotTrrntzipped,
	BadDirectorySeparator,
	Unsorted,
	ExtraDirectoryEntries,
	RepeatFilesFound;
}
