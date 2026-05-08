package com.family.finance.web.todo;

import com.family.finance.auth.MemberPrincipal;
import com.family.finance.domain.period.Period;
import com.family.finance.service.EntryService;
import com.family.finance.service.NavService;
import com.family.finance.service.PeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class MyTodosController {

    private final PeriodService periodService;
    private final EntryService entryService;
    private final NavService navService;

    @GetMapping("/my-todos")
    public String myTodos(@AuthenticationPrincipal MemberPrincipal me, Model model) {
        Period period = periodService.findCurrentOpen(me.getFamilyId())
                .orElse(null);
        model.addAttribute("me", me);
        model.addAttribute("nav", navService.load(me));
        model.addAttribute("period", period);
        model.addAttribute("rows", period == null
                ? java.util.List.of()
                : entryService.listRows(me.getFamilyId(), me.getMemberId(), period, true));
        return "my-todos";
    }
}
