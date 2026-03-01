package com.smartquotes.quoteservice.dto;

import com.smartquotes.quoteservice.enums.InteractionType;

public record InteractionRequest(
        InteractionType interactionType
) {}