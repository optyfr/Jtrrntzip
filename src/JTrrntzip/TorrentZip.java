package JTrrntzip;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.FilenameUtils;

import JTrrntzip.SupportedFiles.ICompress;
import JTrrntzip.SupportedFiles.SevenZip.SevenZ;
import JTrrntzip.SupportedFiles.ZipFile.ZipFile;

public class TorrentZip
{
	public StatusCallback StatusCallBack;
	public LogCallback StatusLogCallBack;
	public int ThreadID;

	private byte[] _buffer;

	public TorrentZip()
	{
		_buffer = new byte[1024 * 1024];
	}

	public TrrntZipStatus Process(File f) throws IOException
	{
		if(Program.VerboseLogging)
			StatusLogCallBack.StatusLogCallBack(ThreadID, "");

		StatusLogCallBack.StatusLogCallBack(ThreadID, f.getName() + " - ");

		// First open the zip (7z) file, and fail out if it is corrupt.

		AtomicReference<ICompress> zipFile = new AtomicReference<>();
		EnumSet<TrrntZipStatus> tzs = OpenZip(f, zipFile);
		// this will return ValidTrrntZip or CorruptZip.

		if(tzs.contains(TrrntZipStatus.CorruptZip))
		{
			StatusLogCallBack.StatusLogCallBack(ThreadID, "Zip file is corrupt");
			return TrrntZipStatus.CorruptZip;
		}

		// the zip file may have found a valid trrntzip header, but we now check that all the file info
		// is actually valid, and may invalidate it being a valid trrntzip if any problem is found.

		List<ZippedFile> zippedFiles = ReadZipContent(zipFile.get());
		tzs.addAll(TorrentZipCheck.CheckZipFiles(zippedFiles));

		// if tza is now just 'ValidTrrntzip' the it is fully valid, and nothing needs to be done to it.

		System.out.println(tzs);
		if(tzs.contains(TrrntZipStatus.ValidTrrntzip) && !Program.ForceReZip || Program.CheckOnly)
		{
			StatusLogCallBack.StatusLogCallBack(ThreadID, "Skipping File");
			return TrrntZipStatus.ValidTrrntzip;
		}
		System.out.println(Program.CheckOnly);
		StatusLogCallBack.StatusLogCallBack(ThreadID, "TorrentZipping");
		TrrntZipStatus fixedTzs = TorrentZipRebuild.ReZipFiles(zippedFiles, zipFile.get(), _buffer, StatusCallBack, StatusLogCallBack, ThreadID);
		return fixedTzs;
	}

	private EnumSet<TrrntZipStatus> OpenZip(File f, AtomicReference<ICompress> zipFile) throws IOException
	{
		String ext = FilenameUtils.getExtension(f.getName());
		if(ext.equalsIgnoreCase("7z"))
			zipFile.set(new SevenZ());
		else
			zipFile.set(new ZipFile());

		ZipReturn zr = zipFile.get().ZipFileOpen(f, f.lastModified(), true);
		if(zr != ZipReturn.ZipGood)
			return EnumSet.of(TrrntZipStatus.CorruptZip);

		EnumSet<TrrntZipStatus> tzStatus = EnumSet.noneOf(TrrntZipStatus.class);

		// first check if the file is a trrntip files
		if(zipFile.get().ZipStatus().contains(ZipStatus.TrrntZip))
			tzStatus.add(TrrntZipStatus.ValidTrrntzip);

		return tzStatus;
	}

	private List<ZippedFile> ReadZipContent(ICompress zipFile)
	{
		List<ZippedFile> zippedFiles = new ArrayList<ZippedFile>();
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
