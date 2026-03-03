package com.example.bnianchorcheckinbackend

import com.example.bnianchorcheckinbackend.entities.Guest
import com.example.bnianchorcheckinbackend.entities.Member
import com.example.bnianchorcheckinbackend.entities.MemberStanding
import com.example.bnianchorcheckinbackend.repositories.GuestRepository
import com.example.bnianchorcheckinbackend.repositories.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class BulkImportRequest(
    val type: String, // "member" or "guest"
    val records: List<ImportRecord>
)

data class ImportRecord(
    val name: String,
    val profession: String,
    val email: String? = null,
    val phoneNumber: String? = null,
    val referrer: String? = null,
    val standing: String? = null,
    val professionCode: String? = null,
    val position: String? = null,
    val membershipId: String? = null,
    val eventDate: String? = null
)

data class ImportResult(
    val total: Int,
    val inserted: Int,
    val updated: Int,
    val failed: Int,
    val errors: List<String>
)

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class BulkImportService(
    private val memberRepository: MemberRepository,
    private val guestRepository: GuestRepository
) {

    @Transactional
    fun bulkImportMembers(records: List<ImportRecord>): ImportResult {
        var inserted = 0
        var updated = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (record in records) {
            try {
                val existingMember = memberRepository.findByNameIgnoreCase(record.name)
                
                if (existingMember.isPresent) {
                    // Update existing member
                    val member = existingMember.get()
                    member.profession = record.profession
                    // Only update email/phone if they're not dummy values
                    if (!record.email.isNullOrBlank() && record.email != "test@gmail.com") {
                        member.email = record.email
                    }
                    if (!record.phoneNumber.isNullOrBlank() && record.phoneNumber != "12345678") {
                        member.phoneNumber = record.phoneNumber
                    }
                    member.professionCode = record.professionCode ?: member.professionCode
                    member.position = record.position ?: member.position
                    member.membershipId = record.membershipId ?: member.membershipId
                    member.standing = try {
                        MemberStanding.valueOf(record.standing?.uppercase() ?: "GREEN")
                    } catch (e: Exception) {
                        MemberStanding.GREEN
                    }
                    memberRepository.save(member)
                    updated++
                } else {
                    // Insert new member - skip email/phone if they're dummy values to avoid unique constraint
                    val email = if (record.email.isNullOrBlank() || record.email == "test@gmail.com") null else record.email
                    val phoneNumber = if (record.phoneNumber.isNullOrBlank() || record.phoneNumber == "12345678") null else record.phoneNumber
                    
                    val member = Member(
                        name = record.name,
                        profession = record.profession,
                        email = email,
                        phoneNumber = phoneNumber,
                        professionCode = record.professionCode ?: "A",
                        position = record.position ?: "Member",
                        membershipId = record.membershipId,
                        standing = try {
                            MemberStanding.valueOf(record.standing?.uppercase() ?: "GREEN")
                        } catch (e: Exception) {
                            MemberStanding.GREEN
                        }
                    )
                    memberRepository.save(member)
                    inserted++
                }
            } catch (e: Exception) {
                failed++
                errors.add("Failed to import ${record.name}: ${e.message}")
            }
        }

        return ImportResult(
            total = records.size,
            inserted = inserted,
            updated = updated,
            failed = failed,
            errors = errors
        )
    }

    @Transactional
    fun bulkImportGuests(records: List<ImportRecord>): ImportResult {
        var inserted = 0
        var updated = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (record in records) {
            try {
                val existingGuest = guestRepository.findByNameIgnoreCase(record.name)
                
                if (existingGuest.isPresent) {
                    // Update existing guest
                    val guest = existingGuest.get()
                    guest.profession = record.profession
                    guest.referrer = record.referrer?.takeIf { it.isNotBlank() }
                    guest.email = record.email?.takeIf { it.isNotBlank() }
                    guest.phoneNumber = record.phoneNumber?.takeIf { it.isNotBlank() }
                    guest.eventDate = record.eventDate?.takeIf { it.isNotBlank() }
                    guestRepository.save(guest)
                    updated++
                } else {
                    // Insert new guest
                    val guest = Guest(
                        name = record.name,
                        profession = record.profession,
                        referrer = record.referrer?.takeIf { it.isNotBlank() },
                        email = record.email?.takeIf { it.isNotBlank() },
                        phoneNumber = record.phoneNumber?.takeIf { it.isNotBlank() },
                        eventDate = record.eventDate?.takeIf { it.isNotBlank() }
                    )
                    guestRepository.save(guest)
                    inserted++
                }
            } catch (e: Exception) {
                failed++
                errors.add("Failed to import ${record.name}: ${e.message}")
            }
        }

        return ImportResult(
            total = records.size,
            inserted = inserted,
            updated = updated,
            failed = failed,
            errors = errors
        )
    }

    fun bulkImport(request: BulkImportRequest): ImportResult {
        return when (request.type.lowercase()) {
            "member" -> bulkImportMembers(request.records)
            "guest" -> bulkImportGuests(request.records)
            else -> ImportResult(
                total = 0,
                inserted = 0,
                updated = 0,
                failed = 0,
                errors = listOf("Invalid type: ${request.type}. Must be 'member' or 'guest'")
            )
        }
    }
}
