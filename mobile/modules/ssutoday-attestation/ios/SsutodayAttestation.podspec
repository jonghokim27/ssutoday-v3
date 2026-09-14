Pod::Spec.new do |s|
  s.name = 'SsutodayAttestation'
  s.version = '0.1.0'
  s.summary = 'SSUTODAY photo capture and App Attest proof generation'
  s.description = s.summary
  s.homepage = 'https://ssu.today'
  s.license = { :type => 'Proprietary' }
  s.author = 'SSUTODAY'
  s.source = { :git => '' }
  s.platforms = { :ios => '15.1' }
  s.swift_version = '5.9'
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.frameworks = 'DeviceCheck', 'CryptoKit'
  s.source_files = '**/*.swift'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
