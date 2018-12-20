@Grab(group='com.drewnoakes', module='metadata-extractor', version='2.6.2')

import java.text.*
import com.drew.imaging.*
import com.drew.metadata.exif.*


args.getFiles{ f -> f.hasExtension('jpg') }.each { f ->
	def metadata = ImageMetadataReader.readMetadata(f)
	
	def exifSubIFD = metadata.getDirectory(ExifSubIFDDirectory.class)
	def dateFormat = new SimpleDateFormat("- yyyy-MM-dd - HH'h' mm'm' ss's'")
	def dateObject = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
	def date = dateFormat.format(dateObject)
	
	def exifIFD0 = metadata.getDirectory(ExifIFD0Directory.class)
	def maker = exifIFD0.getString(ExifIFD0Directory.TAG_MAKE)
	def model = exifIFD0.getString(ExifIFD0Directory.TAG_MODEL)
	
	log.finest "$maker $model $date"
	
	if (maker && model && date) {
		def dest = new File(f.parentFile, "$maker $model $date" + '.' + f.extension.lower())
		if (!f.equals(dest)) {
			if (f.renameTo(dest)) {
				println "[RENAME] [$f.name] to [$dest.name]"
			}
		}
	}
}
