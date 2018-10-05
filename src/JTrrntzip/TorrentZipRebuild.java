package JTrrntzip;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.DeflaterOutputStream;

import org.apache.commons.io.FilenameUtils;

import JTrrntzip.SupportedFiles.ICompress;
import JTrrntzip.SupportedFiles.ZipFile.ZipFile;

public final class TorrentZipRebuild
{

	public final static EnumSet<TrrntZipStatus> ReZipFiles(final List<ZippedFile> zippedFiles, final ICompress originalZipFile, final byte[] buffer, final LogCallback LogCallback)
	{
		assert originalZipFile!=null;
		final int bufferSize = buffer.length;
		final File filename = new File(originalZipFile.ZipFilename());
		final File tmpFilename = new File(filename.getParentFile(), FilenameUtils.getBaseName(filename.getName()) + ".tmp"); //$NON-NLS-1$

		final File outfilename = new File(filename.getParentFile(), FilenameUtils.getBaseName(filename.getName()) + ".zip"); //$NON-NLS-1$

		if (tmpFilename.exists())
			tmpFilename.delete();

		final ICompress zipFileOut = new ZipFile();

		try
		{
			zipFileOut.ZipFileCreate(tmpFilename);

			// by now the zippedFiles have been sorted so just loop over them
			for (int i = 0; i < zippedFiles.size(); i++)
			{
				LogCallback.StatusCallBack((int) ((double) (i + 1) / (zippedFiles.size()) * 100));

				final ZippedFile t = zippedFiles.get(i);

				if (LogCallback.isVerboseLogging())
					LogCallback.StatusLogCallBack(String.format("%15s %s %s", t.Size, t.toString(), t.Name)); //$NON-NLS-1$

				final AtomicReference<InputStream> readStream = new AtomicReference<>();
				final AtomicReference<BigInteger> streamSize = new AtomicReference<>();
				final AtomicInteger compMethod = new AtomicInteger();

				ZipReturn zrInput = ZipReturn.ZipUntested;
				ZipFile z = null;
				if (originalZipFile instanceof ZipFile)
				{
					z = (ZipFile) originalZipFile;
					if (z != null)
						zrInput = z.ZipFileOpenReadStream(t.Index, false, readStream, streamSize, compMethod);
				}

				final AtomicReference<OutputStream> writeStream = new AtomicReference<>();
				final ZipReturn zrOutput = zipFileOut.ZipFileOpenWriteStream(false, true, t.Name, streamSize.get(), (short) 8, writeStream);

				if (zrInput != ZipReturn.ZipGood || zrOutput != ZipReturn.ZipGood)
				{
					// Error writing local File.
					zipFileOut.ZipFileClose();
					zipFileOut.close();
					originalZipFile.ZipFileClose();
					tmpFilename.delete();
					return EnumSet.of(TrrntZipStatus.CorruptZip);
				}

				final CheckedInputStream crcCs = new CheckedInputStream(readStream.get(), new CRC32());
				final BufferedInputStream bcrcCs = new BufferedInputStream(crcCs, buffer.length);
				final BufferedOutputStream bWriteStream = new BufferedOutputStream(writeStream.get(), buffer.length);

				BigInteger sizetogo = streamSize.get();
				while (sizetogo.compareTo(BigInteger.valueOf(0)) > 0)
				{
					final int sizenow = sizetogo.compareTo(BigInteger.valueOf(bufferSize)) > 0 ? bufferSize : sizetogo.intValue();
					bcrcCs.read(buffer, 0, sizenow);
					bWriteStream.write(buffer, 0, sizenow);
					sizetogo = sizetogo.subtract(BigInteger.valueOf(sizenow));
				}
				bWriteStream.flush();
				if (writeStream.get() instanceof DeflaterOutputStream)
					((DeflaterOutputStream) writeStream.get()).finish();

				if (z != null)
					originalZipFile.ZipFileCloseReadStream();

				final long crc = crcCs.getChecksum().getValue();

				if ((int) crc != t.CRC)
					return EnumSet.of(TrrntZipStatus.CorruptZip);

				zipFileOut.ZipFileCloseWriteStream(t.getCRC());
			}

			zipFileOut.ZipFileClose();
			zipFileOut.close();
			originalZipFile.ZipFileClose();
			originalZipFile.close();
			if(!filename.equals(outfilename))
				filename.delete();
			Files.copy(tmpFilename.toPath(), outfilename.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
			tmpFilename.delete();
			return EnumSet.of(TrrntZipStatus.ValidTrrntzip);

		}
		catch (final Throwable e)
		{
			e.printStackTrace();
			Optional.ofNullable(zipFileOut).ifPresent(t -> {
				try
				{
					t.ZipFileCloseFailed();
					t.close();
				}
				catch (final IOException e1)
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
				catch (final IOException e1)
				{
					e1.printStackTrace();
				}
			});
			return EnumSet.of(TrrntZipStatus.CorruptZip);
		}

	}

}
