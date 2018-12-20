// filebot -script fn:replace --action test --filter "[.]srt$" --def "e=[.](eng|english)" "r=.en"


def accept = { f -> _args.filter ? f.path =~ _args.filter : true }
def pattern = Pattern.compile(e, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)

def renamePlan = [:]

args.getFiles{ f -> accept(f) && pattern.matcher(f.path).find() }.each{ f ->
	renamePlan[f] = pattern.matcher(f.path).replaceAll(r) as File
}

rename(map:renamePlan)
