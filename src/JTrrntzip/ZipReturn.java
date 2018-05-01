package JTrrntzip;

public enum ZipReturn
{
    ZipGood,
    ZipFileLocked,
    ZipFileCountError,
    ZipSignatureError,
    ZipExtraDataOnEndOfZip,
    ZipUnsupportedCompression,
    ZipLocalFileHeaderError,
    ZipCentralDirError,
    ZipEndOfCentralDirectoryError,
    Zip64EndOfCentralDirError,
    Zip64EndOfCentralDirectoryLocatorError,
    ZipReadingFromOutputFile,
    ZipWritingToInputFile,
    ZipErrorGettingDataStream,
    ZipCRCDecodeError,
    ZipDecodeError,
    ZipFileNameToLong,
    ZipFileAlreadyOpen,
    ZipCannotFastOpen,
    ZipErrorOpeningFile,
    ZipErrorFileNotFound,
    ZipErrorReadingFile,
    ZipErrorTimeStamp,
    ZipErrorRollBackFile,
    ZipTryingToAccessADirectory,
    ZipUntested
}
