package com.gurukul.vendors.service;

import com.gurukul.common.EntityNotFoundException;
import com.gurukul.common.SchoolContext;
import com.gurukul.vendors.dto.VendorRequest;
import com.gurukul.vendors.dto.VendorResponse;
import com.gurukul.vendors.entity.Vendor;
import com.gurukul.vendors.repository.VendorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VendorService {

	private final VendorRepository vendorRepository;
	private final SchoolContext schoolContext;

	public List<VendorResponse> list() {
		return vendorRepository.findAllBySchoolIdOrderByNameAsc(schoolContext.getSchoolId()).stream()
				.map(VendorResponse::from)
				.toList();
	}

	public VendorResponse getById(UUID id) {
		return VendorResponse.from(findScoped(id));
	}

	@Transactional
	public VendorResponse create(VendorRequest request) {
		Vendor vendor = new Vendor();
		vendor.setSchoolId(schoolContext.getSchoolId());
		applyRequest(vendor, request);
		return VendorResponse.from(vendorRepository.save(vendor));
	}

	@Transactional
	public VendorResponse update(UUID id, VendorRequest request) {
		Vendor vendor = findScoped(id);
		applyRequest(vendor, request);
		return VendorResponse.from(vendorRepository.save(vendor));
	}

	public Vendor getScopedEntity(UUID id) {
		return findScoped(id);
	}

	private Vendor findScoped(UUID id) {
		return vendorRepository.findByIdAndSchoolId(id, schoolContext.getSchoolId())
				.orElseThrow(() -> new EntityNotFoundException("Vendor not found"));
	}

	private void applyRequest(Vendor vendor, VendorRequest request) {
		vendor.setName(request.getName());
		vendor.setContactPhone(request.getContactPhone());
		vendor.setContactEmail(request.getContactEmail());
		vendor.setBankAccount(request.getBankAccount());
		vendor.setUpiId(request.getUpiId());
		vendor.setAddress(request.getAddress());
	}

}
