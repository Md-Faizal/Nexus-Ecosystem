import sys
import json
import urllib.request
import oci

try:
    req = urllib.request.urlopen('http://169.254.169.254/opc/v1/instance/')
    imdata = json.loads(req.read().decode())
    print(f"VM Instance OCID:    {imdata.get('id')}")
    print(f"Compartment OCID:    {imdata.get('compartmentId')}")
    print(f"Region:              {imdata.get('canonicalRegionName')}")
except Exception as e:
    print(f"Metadata error: {e}")

stack_ocid = "ocid1.ormstack.oc1.ap-hyderabad-1.amaaaaaae3t6lgqavlngst2cjhzru45xfnsa3ul4tb7weubebag5gxrzmqga"
print(f"Target Stack OCID:   {stack_ocid}\n")

# 1. Test Instance Principals
print("--- 1. Testing Instance Principals ---")
try:
    signer = oci.auth.signers.InstancePrincipalsSecurityTokenSigner()
    rm_client = oci.resource_manager.ResourceManagerClient(config={}, signer=signer)
    stack = rm_client.get_stack(stack_ocid).data
    print(f"[SUCCESS] Authenticated via Instance Principal!")
    print(f"Stack Name: {stack.display_name}, Lifecycle State: {stack.lifecycle_state}")
    sys.exit(0)
except Exception as e:
    print(f"[INFO] Instance Principal: {e}\n")

# 2. Test standard ~/.oci/config
print("--- 2. Testing ~/.oci/config ---")
try:
    config = oci.config.from_file()
    rm_client = oci.resource_manager.ResourceManagerClient(config)
    stack = rm_client.get_stack(stack_ocid).data
    print(f"[SUCCESS] Authenticated via ~/.oci/config!")
    print(f"Stack Name: {stack.display_name}, Lifecycle State: {stack.lifecycle_state}")
    sys.exit(0)
except Exception as e:
    print(f"[INFO] ~/.oci/config: {e}\n")
