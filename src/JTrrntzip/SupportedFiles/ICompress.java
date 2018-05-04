package JTrrntzip.SupportedFiles;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import JTrrntzip.ZipOpenType;
import JTrrntzip.ZipReturn;
import JTrrntzip.ZipStatus;

public interface ICompress extends Closeable, AutoCloseable
{
	int LocalFilesCount();

	String Filename(int i);

	BigInteger LocalHeader(int i);

	BigInteger UncompressedSize(int i);

	byte[] CRC32(int i);

	ZipReturn FileStatus(int i);

	ZipOpenType ZipOpen();

	ZipReturn ZipFileOpen(File newFilename, long timestamp, boolean readHeaders) throws IOException;

	void ZipFileClose() throws IOException;

	ZipReturn ZipFileOpenWriteStream(boolean raw, boolean trrntzip, String filename, BigInteger uncompressedSize, short compressionMethod, AtomicReference<OutputStream> stream) throws IOException;

	ZipReturn ZipFileCloseReadStream() throws IOException;

	void DeepScan();

	EnumSet<ZipStatus> ZipStatus();

	String ZipFilename();

	long TimeStamp();

	void ZipFileAddDirectory() throws IOException;

	ZipReturn ZipFileCreate(File newFilename) throws IOException;

	ZipReturn ZipFileCloseWriteStream(byte[] crc32) throws IOException;

	ZipReturn ZipFileRollBack() throws IOException;

	void ZipFileCloseFailed() throws IOException;
}
