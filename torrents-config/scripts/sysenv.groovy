// filebot -script fn:sysenv

println '# Environment Variables #'
_environment.each{
	println "$it.key: $it.value"
}
println '\n'

println '# Java System Properties #'
_system.each{
	println "$it.key: $it.value"
}
println '\n'

println '# Arguments #'
_args.array.eachWithIndex{ a, i ->
	println "args[$i] = $a"
}
println '\n'
