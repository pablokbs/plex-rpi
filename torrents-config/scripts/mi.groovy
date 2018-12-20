// filebot -script fn:mi /path/to/media/ "MediaIndex.csv"

/*
 * Print media info of all video files to CSV file  
 */
def model    = '''Name;Container;Resolution;Video Codec;Video Format;Audio Codec;Audio Format;Audio Language(s);Subtitle Language(s);Duration;File Size;Folder Size;Folder Count;Path'''
def template = '''{fn};{cf};{resolution};{vc};{vf};{ac};{af};{media.AudioLanguageList};{media.TextLanguageList};{media.DurationString3};{file.length()};{folder.listFiles().sum{ it.length() }};{folder.listFiles().sum{ it.isFile() ? 1 : 0 }};{file.getCanonicalPath()}'''

def csvFile  = args[-1] as File

if (csvFile.name.endsWith('.csv')) {
	// open destination file (writing files requires -trust-script)
	csvFile.withWriter('UTF-8'){ output ->
		// print to console
		println "Writing CSV file [$csvFile]"
		println model

		// print header
		output.write(model)
		output.write('\n')

		// print info for each video file (sorted by filename)
		args.getFiles{ it.isVideo() }.sort{ a, b -> a.name.compareToIgnoreCase(b.name) }.each{
			def mi = getMediaInfo(it, template)

			// print to console
			println mi

			// append to file
			output.write(mi)
			output.write('\n')
		}
	}	
} else {
	// pipe usage
	getMediaInfo(args.getFiles{ it.isVideo() }, template).each{ println it }
	System.exit(0)
}
