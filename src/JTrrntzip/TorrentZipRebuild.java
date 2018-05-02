package JTrrntzip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.DeflaterOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import JTrrntzip.SupportedFiles.ICompress;
import JTrrntzip.SupportedFiles.ZipFile.ZipFile;

public final class TorrentZipRebuild
{

	public final static TrrntZipStatus ReZipFiles(List<ZippedFile> zippedFiles, ICompress originalZipFile, byte[] buffer, LogCallback LogCallback)
	{
		int bufferSize = buffer.length;

		File filename = new File(originalZipFile.ZipFilename());
		File tmpFilename = new File(filename.getParentFile(), FilenameUtils.getBaseName(filename.getName()) + ".tmp");

		File outfilename = new File(filename.getParentFile(), FilenameUtils.getBaseName(filename.getName()) + ".zip");

		if(tmpFilename.exists())
			tmpFilename.delete();

		ICompress zipFileOut = new ZipFile();

		try
		{
			zipFileOut.ZipFileCreate(tmpFilename);

			// by now the zippedFiles have been sorted so just loop over them
			for(int i = 0; i < zippedFiles.size(); i++)
			{
				LogCallback.StatusCallBack((int) ((double) (i + 1) / (zippedFiles.size()) * 100));

				ZippedFile t = zippedFiles.get(i);

				if(LogCallback.isVerboseLogging())
					LogCallback.StatusLogCallBack(String.format("%15s %s %s", t.Size, t.toString(), t.Name));

				AtomicReference<InputStream> readStream = new AtomicReference<>();
				AtomicLong streamSize = new AtomicLong();
				AtomicInteger compMethod = new AtomicInteger();

				ZipReturn zrInput = ZipReturn.ZipUntested;
				ZipFile z = null;
				if(originalZipFile instanceof ZipFile)
				{
					z = (ZipFile) originalZipFile;
					if(z != null)
						zrInput = z.ZipFileOpenReadStream(t.Index, false, readStream, streamSize, compMethod);
				}

				AtomicReference<OutputStream> writeStream = new AtomicReference<>();
				ZipReturn zrOutput = zipFileOut.ZipFileOpenWriteStream(false, true, t.Name, streamSize.get(), (short) 8, writeStream);

				if(zrInput != ZipReturn.ZipGood || zrOutput != ZipReturn.ZipGood)
				{
					// Error writing local File.
					zipFileOut.ZipFileClose();
					zipFileOut.close();
					originalZipFile.ZipFileClose();
					tmpFilename.delete();
					return TrrntZipStatus.CorruptZip;
				}

				CheckedInputStream crcCs = new CheckedInputStream(readStream.get(), new CRC32());
				BufferedInputStream bcrcCs = new BufferedInputStream(crcCs, buffer.length);
				BufferedOutputStream bWriteStream = new BufferedOutputStream(writeStream.get(), buffer.length);

				long sizetogo = streamSize.get();
				while(sizetogo > 0)
				{
					int sizenow = sizetogo > (long) bufferSize ? bufferSize : (int) sizetogo;

					bcrcCs.read(buffer, 0, sizenow);
					bWriteStream.write(buffer, 0, sizenow);
					sizetogo = sizetogo - (long) sizenow;
				}
				bWriteStream.flush();
				if(writeStream.get() instanceof DeflaterOutputStream)
					((DeflaterOutputStream) writeStream.get()).finish();

				if(z != null)
					originalZipFile.ZipFileCloseReadStream();

				long crc = crcCs.getChecksum().getValue();

				if((int) crc != t.CRC)
					return TrrntZipStatus.CorruptZip;

				zipFileOut.ZipFileCloseWriteStream(t.getCRC());
			}

			zipFileOut.ZipFileClose();
			zipFileOut.close();
			originalZipFile.ZipFileClose();
			originalZipFile.close();
			filename.delete();
			FileUtils.moveFile(tmpFilename, outfilename);
			return TrrntZipStatus.ValidTrrntzip;

		}
		catch(Throwable e)
		{
			e.printStackTrace();
			Optional.ofNullable(zipFileOut).ifPresent(t -> {
				try
				{
					t.ZipFileCloseFailed();
					t.close();
				}
				catch(IOException e1)
				{
					e1.printStackTrace();
				}
			});
			Optional.ofNullable(originalZipFile).ifPresent(t -> {
				try
				{
					t.ZipFileClose();
					t.close();
				}
				catch(IOException e1)
				{
					e1.printStackTrace();
				}
			});
			return TrrntZipStatus.CorruptZip;
		}

	}

}
