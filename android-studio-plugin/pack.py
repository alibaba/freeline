import re
import os

text = open('./resources/META-INF/plugin.xml').read()
pattern = re.compile(r"<version>(.*)</version>")
version = ""
for m in pattern.finditer(text):
	version = m.group(1)
print(version)
os.rename('./android-studio-plugin.jar', "./freeline-plugin-%s.jar"%(version))	

