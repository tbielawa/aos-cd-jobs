#!/usr/bin/env bash

# Usage:
#	$ ./art674.sh < input.json > output.adoc

cat <<-EOF
// Module included in the following assemblies:
//
// * installing/installing_aws_user_infra/installing-aws-user-infra.adoc

[id="installation-aws-user-infra-rhcos-ami_{context}"]
= {op-system} AMIs for the AWS infrastructure

You must use a valid {op-system-first} AMI for your Amazon Web Services
(AWS) zone for your {product-title} nodes.

.{op-system} AMIs

[cols="2a,2a",options="header"]
|===

|AWS zone
|AWS AMI

EOF
jq -r '.amis[]|"|`\(.name)`\n|`\(.hvm)`\n"' <&0
echo '|==='
