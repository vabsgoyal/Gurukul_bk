package com.gurukul.documents;

import org.springframework.stereotype.Service;

@Service
public class ReceiptService {

	public String getReceiptReference(String receiptNumber) {
		return receiptNumber;
	}

}
