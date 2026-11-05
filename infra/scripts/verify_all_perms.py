import oci

s = oci.auth.signers.InstancePrincipalsSecurityTokenSigner()

# 1. Test Subnet
vc = oci.core.VirtualNetworkClient({"region": "ap-hyderabad-1"}, signer=s)
sub_id = "ocid1.subnet.oc1.ap-hyderabad-1.aaaaaaaas7cuu754qmcwaibvnsfn3lkrdot2kpcpv7ia2fla4uvvnctcrfua"
try:
    sub = vc.get_subnet(sub_id).data
    print(f"[OK] Subnet accessible: {sub.display_name} ({sub.lifecycle_state})")
except Exception as e:
    print(f"[FAIL] Subnet error: {e}")

# 2. Test Compute
c = oci.core.ComputeClient({"region": "ap-hyderabad-1"}, signer=s)
img_id = "ocid1.image.oc1.ap-hyderabad-1.aaaaaaaatbrz524dmtydbpqspwawkmtfkx6dcnwpsy5yg7d3hop25urqaazq"
try:
    img = c.get_image(img_id).data
    print(f"[OK] Image accessible: {img.display_name} ({img.lifecycle_state})")
except Exception as e:
    print(f"[FAIL] Image error: {e}")

# 3. Test Resource Manager
rm = oci.resource_manager.ResourceManagerClient({"region": "ap-hyderabad-1"}, signer=s)
stack_id = "ocid1.ormstack.oc1.ap-hyderabad-1.amaaaaaae3t6lgqavlngst2cjhzru45xfnsa3ul4tb7weubebag5gxrzmqga"
try:
    st = rm.get_stack(stack_id).data
    print(f"[OK] Stack accessible: {st.display_name} ({st.lifecycle_state})")
except Exception as e:
    print(f"[FAIL] Stack error: {e}")
