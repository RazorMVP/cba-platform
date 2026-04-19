package com.cba.system;

import com.cba.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Surveys", description = "PPI / welfare survey engine — define question-response surveys and collect scored client assessments")
@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @Operation(summary = "List all surveys")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<Survey>> list(Pageable pageable) {
        return ApiResponse.ok(surveyService.listSurveys(pageable));
    }

    @Operation(summary = "Get a survey by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Survey> get(@PathVariable UUID id) {
        return ApiResponse.ok(surveyService.getSurvey(id));
    }

    @Operation(summary = "Get a survey by its unique key")
    @GetMapping("/key/{key}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Survey> getByKey(@PathVariable String key) {
        return ApiResponse.ok(surveyService.getSurveyByKey(key));
    }

    @Operation(summary = "Create a new survey with questions and responses")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Survey> create(@RequestBody SurveyService.CreateSurveyRequest req) {
        return ApiResponse.ok(surveyService.createSurvey(req));
    }

    @Operation(summary = "Update a survey")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Survey> update(@PathVariable UUID id,
            @RequestBody SurveyService.CreateSurveyRequest req) {
        return ApiResponse.ok(surveyService.updateSurvey(id, req));
    }

    @Operation(summary = "Delete a survey")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        surveyService.deleteSurvey(id);
    }

    @Operation(summary = "List scorecards submitted for a survey")
    @GetMapping("/{id}/scorecards")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<List<SurveyScorecard>> scorecards(@PathVariable UUID id) {
        return ApiResponse.ok(surveyService.getScorecards(id));
    }

    @Operation(summary = "Submit a completed scorecard for a survey")
    @PostMapping("/{id}/scorecards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<SurveyScorecard> submitScorecard(
            @PathVariable UUID id,
            @RequestBody SurveyService.SubmitScorecardRequest req) {
        return ApiResponse.ok(surveyService.submitScorecard(req));
    }
}
