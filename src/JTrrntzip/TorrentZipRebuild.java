package JTrrntzip;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
	private TorrentZipRebuild()
	{
		throw new IllegalStateException("Utility class");
	}

	public static final Set<TrrntZipStatus> ReZipFiles(final List<ZippedFile> zippedFiles, final ICompress originalZipFile, final byte[] buffer, final LogCallback LogCallback)
	{
		if (originalZipFile == null)
			throw new IllegalArgumentException("original zip file is <null>");

		final var filename = Path.of(originalZipFile.ZipFilename());
		final var tmpFilename = filename.getParent().resolve(FilenameUtils.getBaseName(filename.getFileName().toString()) + ".tmp"); //$NON-NLS-1$
		final var outfilename = filename.getParent().resolve(FilenameUtils.getBaseName(filename.getFileName().toString()) + ".zip"); //$NON-NLS-1$

		try
		{
			return ReZipFiles(zippedFiles, originalZipFile, buffer, LogCallback, filename, tmpFilename, outfilename);
		}
		catch (final Exception e)
		{
			Optional.ofNullable(originalZipFile).ifPresent(t -> {
				try
				{
					t.ZipFileClose();
					t.close();
				}
				catch (final IOException e1)
				{
					System.err.println(e1.getMessage());
				}
			});
			return EnumSet.of(TrrntZipStatus.CorruptZip);
		}

	}

	/**
	 * @param zippedFiles
	 * @param originalZipFile
	 * @param buffer
	 * @param LogCallback
	 * @param filename
	 * @param tmpFilename
	 * @param outfilename
	 * @return
	 * @throws IOException
	 * @throws Exception
	 */
	private static Set<TrrntZipStatus> ReZipFiles(final List<ZippedFile> zippedFiles, final ICompress originalZipFile, final byte[] buffer, final LogCallback LogCallback, final Path filename, final Path tmpFilename, final Path outfilename) throws IOException, Exception
	{
		Files.deleteIfExists(tmpFilename);
		try (ICompress zipFileOut = new ZipFile())
		{
			try
			{
				zipFileOut.ZipFileCreate(tmpFilename.toFile());

				// by now the zippedFiles have been sorted so just loop over them
				for (var i = 0; i < zippedFiles.size(); i++)
				{
					LogCallback.StatusCallBack((int) ((double) (i + 1) / (zippedFiles.size()) * 100));

					final var t = zippedFiles.get(i);

					if (LogCallback.isVerboseLogging())
						LogCallback.StatusLogCallBack(String.format("%15s %s %s", t.Size, t.toString(), t.Name)); //$NON-NLS-1$

					final AtomicReference<InputStream> readStream = new AtomicReference<>();
					final AtomicReference<BigInteger> streamSize = new AtomicReference<>();
					final var compMethod = new AtomicInteger();

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
						Files.delete(tmpFilename);
						return EnumSet.of(TrrntZipStatus.CorruptZip);
					}

					final var crcCs = new CheckedInputStream(readStream.get(), new CRC32());
					final var bcrcCs = new BufferedInputStream(crcCs, buffer.length);
					final var bWriteStream = new BufferedOutputStream(writeStream.get(), buffer.length);

					BigInteger sizetogo = streamSize.get();
					while (sizetogo.compareTo(BigInteger.valueOf(0)) > 0)
					{
						final int sizenow = sizetogo.compareTo(BigInteger.valueOf(buffer.length)) > 0 ? buffer.length : sizetogo.intValue();
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
			}
			catch (Exception e)
			{
				if (zipFileOut != null)
					zipFileOut.ZipFileCloseFailed();
				throw e;
			}
		}
		originalZipFile.ZipFileClose();
		originalZipFile.close();
		if (!filename.equals(outfilename))
			Files.delete(filename);
		Files.copy(tmpFilename, outfilename, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
		Files.delete(tmpFilename);
		return EnumSet.of(TrrntZipStatus.ValidTrrntzip);
	}

}
