package com.cba.system;

import com.cba.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Page<Survey>> list(Pageable pageable) {
        return ApiResponse.ok(surveyService.listSurveys(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Survey> get(@PathVariable UUID id) {
        return ApiResponse.ok(surveyService.getSurvey(id));
    }

    @GetMapping("/key/{key}")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<Survey> getByKey(@PathVariable String key) {
        return ApiResponse.ok(surveyService.getSurveyByKey(key));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Survey> create(@RequestBody SurveyService.CreateSurveyRequest req) {
        return ApiResponse.ok(surveyService.createSurvey(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Survey> update(@PathVariable UUID id,
            @RequestBody SurveyService.CreateSurveyRequest req) {
        return ApiResponse.ok(surveyService.updateSurvey(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id) {
        surveyService.deleteSurvey(id);
    }

    @GetMapping("/{id}/scorecards")
    @PreAuthorize("hasAnyRole('ADMIN','TELLER')")
    public ApiResponse<List<SurveyScorecard>> scorecards(@PathVariable UUID id) {
        return ApiResponse.ok(surveyService.getScorecards(id));
    }

    @PostMapping("/{id}/scorecards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TELLER','CUSTOMER')")
    public ApiResponse<SurveyScorecard> submitScorecard(
            @PathVariable UUID id,
            @RequestBody SurveyService.SubmitScorecardRequest req) {
        return ApiResponse.ok(surveyService.submitScorecard(req));
    }
}
