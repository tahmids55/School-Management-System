package com.school.academic;

import com.school.academic.AcademicDtos.SubjectRequest;
import com.school.academic.AcademicDtos.SubjectResponse;
import com.school.common.exception.BusinessRuleException;
import com.school.common.exception.ConflictException;
import com.school.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository repository;

    @Mock
    private ClassSubjectRepository classSubjectRepository;

    private SubjectService service;

    @BeforeEach
    void setUp() {
        service = new SubjectService(repository, classSubjectRepository);
    }

    private static Subject subject(long id, String code) {
        Subject subject = new Subject(code, "Physics", 100, 33);
        ReflectionTestUtils.setField(subject, "id", id);
        return subject;
    }

    @Test
    void list_returnsRepositoryOrderByCode() {
        when(repository.findAllByOrderByCodeAsc()).thenReturn(List.of(subject(2, "BAN101"), subject(1, "PHY101")));

        assertThat(service.list()).extracting(SubjectResponse::code).containsExactly("BAN101", "PHY101");
    }

    @Test
    void create_trimsAndUpperCasesCode_andTrimsName() {
        when(repository.existsByCode("PHY101")).thenReturn(false);
        when(repository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SubjectResponse response = service.create(new SubjectRequest("  phy101 ", " Physics ", 100, 33));

        assertThat(response.code()).isEqualTo("PHY101");
        assertThat(response.name()).isEqualTo("Physics");
        assertThat(response.fullMarks()).isEqualTo(100);
        assertThat(response.passMarks()).isEqualTo(33);
    }

    @Test
    void create_duplicateCode_is409() {
        when(repository.existsByCode("PHY101")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SubjectRequest("phy101", "Physics", 100, 33)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Subject code PHY101 already exists");
        verify(repository, never()).save(any());
    }

    @Test
    void create_passEqualToFull_isAccepted() {
        when(repository.existsByCode("ICT101")).thenReturn(false);
        when(repository.save(any(Subject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(new SubjectRequest("ICT101", "ICT", 50, 50)).passMarks()).isEqualTo(50);
    }

    @Test
    void create_passGreaterThanFull_is422() {
        assertThatThrownBy(() -> service.create(new SubjectRequest("ICT101", "ICT", 50, 51)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Pass marks cannot be greater than full marks");
        verify(repository, never()).save(any());
    }

    @Test
    void update_codeOfAnotherSubject_is409() {
        when(repository.findById(1L)).thenReturn(Optional.of(subject(1, "PHY101")));
        when(repository.existsByCodeAndIdNot("CHE101", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new SubjectRequest("che101", "Chemistry", 100, 33)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Subject code CHE101 already exists");
    }

    @Test
    void update_changesAllFields() {
        Subject existing = subject(1, "PHY101");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByCodeAndIdNot("PHY102", 1L)).thenReturn(false);
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        SubjectResponse response = service.update(1L, new SubjectRequest("phy102", "Physics II", 75, 25));

        assertThat(response.code()).isEqualTo("PHY102");
        assertThat(response.name()).isEqualTo("Physics II");
        assertThat(response.fullMarks()).isEqualTo(75);
        assertThat(response.passMarks()).isEqualTo(25);
    }

    @Test
    void update_unknownId_is404() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(42L, new SubjectRequest("PHY101", "Physics", 100, 33)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subject 42 was not found");
    }

    @Test
    void delete_deletesAndFlushes() {
        Subject existing = subject(1, "PHY101");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(classSubjectRepository.existsBySubjectId(1L)).thenReturn(false);

        service.delete(1L);

        verify(repository).delete(existing);
        verify(repository).flush();
    }

    @Test
    void delete_assignedToClass_is409WithExactMessage() {
        Subject existing = subject(1, "PHY101");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(classSubjectRepository.existsBySubjectId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Subject PHY101 is assigned to classes and cannot be deleted");
        verify(repository, never()).delete(any());
    }

    @Test
    void delete_unknownId_is404() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subject 42 was not found");
    }
}
