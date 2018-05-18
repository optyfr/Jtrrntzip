package JTrrntzip;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import JTrrntzip.SupportedFiles.ICompress;
import JTrrntzip.SupportedFiles.ZipFile.ZipFile;

public final class TorrentZip
{
	private final LogCallback statusLogCallBack;
	private final TorrentZipOptions options;

	private final byte[] buffer;

	public TorrentZip(final LogCallback statusLogCallBack, final TorrentZipOptions options)
	{
		this.statusLogCallBack = statusLogCallBack;
		this.options = options;
		buffer = new byte[8 * 1024];
	}

	public final EnumSet<TrrntZipStatus> Process(final File f) throws IOException
	{
		if(statusLogCallBack.isVerboseLogging())
			statusLogCallBack.StatusLogCallBack(""); //$NON-NLS-1$

		statusLogCallBack.StatusLogCallBack(f.getName() + " - "); //$NON-NLS-1$

		// First open the zip file, and fail out if it is corrupt.

		final AtomicReference<ICompress> zipFile = new AtomicReference<>();
		final EnumSet<TrrntZipStatus> tzs = OpenZip(f, zipFile);
		// this will return ValidTrrntZip or CorruptZip.

		if(tzs.contains(TrrntZipStatus.CorruptZip))
		{
			statusLogCallBack.StatusLogCallBack(Messages.getString("TorrentZip.ZipFileCorrupt")); //$NON-NLS-1$
			return tzs;
		}

		// the zip file may have found a valid trrntzip header, but we now check that all the file info
		// is actually valid, and may invalidate it being a valid trrntzip if any problem is found.

		final List<ZippedFile> zippedFiles = ReadZipContent(zipFile.get());
		tzs.addAll(TorrentZipCheck.CheckZipFiles(zippedFiles,statusLogCallBack));

		// if tza is now just 'ValidTrrntzip' the it is fully valid, and nothing needs to be done to it.

		if(tzs.contains(TrrntZipStatus.ValidTrrntzip) && !options.isForceRezip())
		{
			statusLogCallBack.StatusLogCallBack(Messages.getString("TorrentZip.SkippingFile")); //$NON-NLS-1$
			return tzs;
		}
		if(options.isCheckOnly())
		{
			statusLogCallBack.StatusLogCallBack(tzs.toString());
			return tzs;
		}
		statusLogCallBack.StatusLogCallBack(Messages.getString("TorrentZip.TorrentZipping")); //$NON-NLS-1$
		final EnumSet<TrrntZipStatus> fixedTzs = TorrentZipRebuild.ReZipFiles(zippedFiles, zipFile.get(), buffer, statusLogCallBack);
		return fixedTzs;
	}

	private final EnumSet<TrrntZipStatus> OpenZip(final File f, final AtomicReference<ICompress> zipFile) throws IOException
	{
		zipFile.set(new ZipFile());

		final ZipReturn zr = zipFile.get().ZipFileOpen(f, f.lastModified(), true);
		if(zr != ZipReturn.ZipGood)
			return EnumSet.of(TrrntZipStatus.CorruptZip);

		final EnumSet<TrrntZipStatus> tzStatus = EnumSet.noneOf(TrrntZipStatus.class);

		// first check if the file is a trrntip files
		if(zipFile.get().ZipStatus().contains(ZipStatus.TrrntZip))
			tzStatus.add(TrrntZipStatus.ValidTrrntzip);

		return tzStatus;
	}

	private final List<ZippedFile> ReadZipContent(final ICompress zipFile)
	{
		final List<ZippedFile> zippedFiles = new ArrayList<>();
		for(int i = 0; i < zipFile.LocalFilesCount(); i++)
		{
			final int ii = i;
			zippedFiles.add(new ZippedFile()
			{
				{
					Index = ii;
					Name = zipFile.Filename(ii);
					setCRC(zipFile.CRC32(ii));
					Size = zipFile.UncompressedSize(ii);
				}
			});
		}
		return zippedFiles;
	}

}
