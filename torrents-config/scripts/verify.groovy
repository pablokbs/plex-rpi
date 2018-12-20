// filebot -script fn:verify /path/to/files

import java.util.concurrent.*
import net.filebot.hash.*


def hashType = HashType.SFV
def xattrkey = 'CRC32'

def files = args.getFiles{ it.isVideo() || it.isAudio() }

def threadPoolSize = Runtime.getRuntime().availableProcessors()
def executor = Executors.newFixedThreadPool(threadPoolSize)

executor.invokeAll(files.collect{ f ->
	return {
		def attr_hash = f.xattr[xattrkey]
		def calc_hash = VerificationUtilities.computeHash(f, hashType)
		
		log.info "$attr_hash $calc_hash $f"

		if (attr_hash == null) {
			log.warning "Set xattr $xattrkey for [$f]"
			f.xattr[xattrkey] = calc_hash

			// verify that xattr has been set correctly
			if (f.xattr[xattrkey] != calc_hash) {
				die "Failed to set xattr $xattrkey for [$f]"
			}
		} else if (attr_hash != calc_hash) {
			die "$xattrkey mismatch (expected $attr_hash, actual: $calc_hash) for [$f]"
		}
	}
})*.get()
